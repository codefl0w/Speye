package com.fl0w.speye.utils

import android.content.Context
import android.content.Intent
import com.fl0w.speye.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DriveAuthManager {
    private const val TAG = "DriveAuthManager"
    private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val WEB_CLIENT_ID = "886147436327-kh7nvccfnl2fhlh8aecrdbrkdmjgmjom.apps.googleusercontent.com"
    private val SCOPES = setOf(Scope(DRIVE_APPDATA_SCOPE))

    private val _account = MutableStateFlow<GoogleSignInAccount?>(null)
    val account: StateFlow<GoogleSignInAccount?> = _account

    fun init(context: Context) {
        _account.value = GoogleSignIn.getLastSignedInAccount(context)
        SpeyeLogger.d(TAG, "Initialized. Current account: ${_account.value?.email}")
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

    fun handleSignInResult(account: GoogleSignInAccount?) {
        _account.value = account
        if (account != null) {
            SpeyeLogger.d(TAG, "Sign-in successful: ${account.email}")
        } else {
            SpeyeLogger.d(TAG, "Sign-in returned null account")
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
        val acc = _account.value ?: return null
        return GoogleAccountCredential.usingOAuth2(context, listOf(DRIVE_APPDATA_SCOPE))
            .setSelectedAccountName(acc.email)
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
            onComplete()
        }
    }
}
