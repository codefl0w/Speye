package com.fl0w.speye.utils

import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import com.fl0w.speye.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object DriveAuthManager {
    private const val TAG = "DriveAuthManager"
    private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val WEB_CLIENT_ID = "886147436327-kh7nvccfnl2fhlh8aecrdbrkdmjgmjom.apps.googleusercontent.com"
    private val SCOPES = setOf(Scope(DRIVE_APPDATA_SCOPE))

    private val _account = MutableStateFlow<GoogleSignInAccount?>(null)
    val account: StateFlow<GoogleSignInAccount?> = _account

    private val _restoredEmail = MutableStateFlow<String?>(null)
    val restoredEmail: StateFlow<String?> = _restoredEmail

    val currentEmail: String?
        get() = _account.value?.email ?: _restoredEmail.value

    fun init(context: Context) {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        _account.value = lastAccount
        val email = lastAccount?.email
        if (email != null) {
            _restoredEmail.value = email
            SpeyeLogger.d(TAG, "Initialized from GoogleSignIn: $email")
            saveRestoreCredential(context, email)
        } else {
            SpeyeLogger.d(TAG, "No cached account, checking Restore Credentials API...")
            tryRestoreCredential(context)
        }
    }

    fun getSignInIntent(context: Context): Intent {
        SpeyeLogger.d(TAG, "Creating sign-in intent with Web Client ID: $WEB_CLIENT_ID")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .requestIdToken(WEB_CLIENT_ID)
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun handleSignInResult(account: GoogleSignInAccount?, context: Context? = null) {
        _account.value = account
        if (account != null) {
            val email = account.email
            _restoredEmail.value = email
            SpeyeLogger.d(TAG, "Sign-in successful: $email")
            if (email != null && context != null) {
                saveRestoreCredential(context, email)
            }
        } else {
            _restoredEmail.value = null
            SpeyeLogger.d(TAG, "Sign-in returned null account")
        }
    }

    fun saveRestoreCredential(context: Context, email: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val requestJson = JSONObject().apply {
                    put("user", JSONObject().apply {
                        put("id", email)
                    })
                    put("account", email)
                    put("type", "google_drive")
                }.toString()

                val createRequest = CreateRestoreCredentialRequest(
                    requestJson = requestJson,
                    isCloudBackupEnabled = true
                )
                credentialManager.createCredential(context, createRequest)
                SpeyeLogger.d(TAG, "Restore credential created/synced for $email")
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Failed to create restore credential for $email", e)
            }
        }
    }

    fun tryRestoreCredential(context: Context, onComplete: ((String?) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val requestJson = JSONObject().apply {
                    put("type", "google_drive")
                }.toString()

                val option = GetRestoreCredentialOption(requestJson)
                val request = GetCredentialRequest(listOf(option))
                val result = credentialManager.getCredential(context, request)
                val cred = result.credential
                if (cred is RestoreCredential) {
                    val json = JSONObject(cred.authenticationResponseJson)
                    val email = json.optString("account").ifEmpty {
                        json.optJSONObject("user")?.optString("id")
                    }
                    if (!email.isNullOrBlank()) {
                        SpeyeLogger.d(TAG, "Zero-Tap restore succeeded: $email")
                        _restoredEmail.value = email
                        withContext(Dispatchers.Main) {
                            onComplete?.invoke(email)
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                SpeyeLogger.d(TAG, "No restore credential available: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(null)
            }
        }
    }

    fun getErrorMessage(context: Context, e: Exception): String {
        return if (e is ApiException) {
            when (e.statusCode) {
                7 -> context.getString(R.string.err_network)
                10 -> context.getString(R.string.err_config)
                12500 -> context.getString(R.string.err_12500)
                12501 -> context.getString(R.string.err_cancelled)
                else -> "Google API Error (${e.statusCode}): ${e.message}"
            }
        } else {
            e.localizedMessage ?: "Unknown authentication error"
        }
    }

    fun getCredential(context: Context): GoogleAccountCredential? {
        val email = currentEmail ?: return null
        return GoogleAccountCredential.usingOAuth2(context, listOf(DRIVE_APPDATA_SCOPE))
            .setSelectedAccountName(email)
    }

    fun createDriveService(context: Context): com.google.api.services.drive.Drive? {
        val credential = getCredential(context) ?: return null
        return com.google.api.services.drive.Drive.Builder(
            com.google.api.client.extensions.android.http.AndroidHttp.newCompatibleTransport(),
            com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Speye").build()
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            _account.value = null
            _restoredEmail.value = null
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    credentialManager.clearCredentialState(
                        ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL)
                    )
                    SpeyeLogger.d(TAG, "Cleared restore credential state")
                } catch (e: Exception) {
                    SpeyeLogger.e(TAG, "Failed to clear restore credential", e)
                }
            }
            onComplete()
        }
    }
}
