package com.fl0w.speye.utils

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.fl0w.speye.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class LiveNotificationAction(
    val title: String,
    val actionIntent: PendingIntent?,
    val isContextual: Boolean = false,
    val remoteInputs: Array<RemoteInput>? = null
) {
    val hasRemoteInput: Boolean
        get() = !remoteInputs.isNullOrEmpty()

    val replyHint: String?
        get() = remoteInputs?.firstOrNull()?.label?.toString()?.takeIf { it.isNotBlank() }
}

data class DecodedNotificationAction(
    val title: String,
    val isTriggered: Boolean
)

object NotificationActionManager {
    private const val TAG = "NotificationActionMgr"
    private const val TRIGGERED_PREFIX = "*"

    private val liveActionsBySbnKey = ConcurrentHashMap<String, List<LiveNotificationAction>>()
    private val triggeredActionBySbnKey = ConcurrentHashMap<String, String>()

    private val _versionFlow = MutableStateFlow(0L)
    val versionFlow: StateFlow<Long> = _versionFlow.asStateFlow()

    fun extractActions(notification: Notification, isMedia: Boolean): List<LiveNotificationAction> {
        if (isMedia) return emptyList()
        val rawActions = notification.actions ?: return emptyList()
        if (rawActions.isEmpty()) return emptyList()

        val result = ArrayList<LiveNotificationAction>(rawActions.size)
        for (action in rawActions) {
            val label = action.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val isContextual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                action.isContextual
            } else {
                false
            }
            val remoteInputs = action.remoteInputs?.takeIf { it.isNotEmpty() }
            result.add(
                LiveNotificationAction(
                    title = label,
                    actionIntent = action.actionIntent,
                    isContextual = isContextual,
                    remoteInputs = remoteInputs
                )
            )
        }
        return result
    }

    fun encodeActionTitles(titles: List<String>, triggeredTitle: String? = null): String? {
        val cleaned = titles.map { it.replace("\n", " ").trim().removePrefix(TRIGGERED_PREFIX) }
            .filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return null
        return cleaned.joinToString("\n") { label ->
            if (triggeredTitle != null && label == triggeredTitle) "$TRIGGERED_PREFIX$label" else label
        }
    }

    fun markTriggeredInEncoded(encoded: String?, triggeredTitle: String?): String? {
        if (encoded.isNullOrBlank()) return null
        if (triggeredTitle == null) return encoded
        val decoded = decodeActionTitles(encoded)
        return encodeActionTitles(decoded, triggeredTitle)
    }

    fun decodeActionTitles(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split("\n")
            .map { it.trim().removePrefix(TRIGGERED_PREFIX).trim() }
            .filter { it.isNotEmpty() }
    }

    fun decodeActionsWithState(encoded: String?): List<DecodedNotificationAction> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { raw ->
                val isTrig = raw.startsWith(TRIGGERED_PREFIX)
                val clean = raw.removePrefix(TRIGGERED_PREFIX).trim()
                DecodedNotificationAction(title = clean, isTriggered = isTrig)
            }
            .filter { it.title.isNotEmpty() }
    }

    fun updateActions(sbnKey: String, actions: List<LiveNotificationAction>) {
        if (actions.isEmpty()) {
            liveActionsBySbnKey.remove(sbnKey)
        } else {
            liveActionsBySbnKey[sbnKey] = actions
            triggeredActionBySbnKey.remove(sbnKey)
        }
        _versionFlow.value = _versionFlow.value + 1L
    }

    fun getTriggeredAction(sbnKey: String?): String? {
        if (sbnKey == null) return null
        return triggeredActionBySbnKey[sbnKey]
    }

    fun onNotificationRemoved(sbnKey: String) {
        liveActionsBySbnKey.remove(sbnKey)
        _versionFlow.value = _versionFlow.value + 1L
    }

    fun hasLiveAction(sbnKey: String?, index: Int, title: String, isSystemRemoved: Boolean): Boolean {
        if (isSystemRemoved || sbnKey == null) return false
        val list = liveActionsBySbnKey[sbnKey] ?: return false
        val match = list.getOrNull(index)?.takeIf { it.title == title }
            ?: list.firstOrNull { it.title == title }
        return match?.actionIntent != null
    }

    fun requiresReplyInput(sbnKey: String?, index: Int, title: String, isSystemRemoved: Boolean): Boolean {
        if (isSystemRemoved || sbnKey == null) return false
        val list = liveActionsBySbnKey[sbnKey] ?: return false
        val match = list.getOrNull(index)?.takeIf { it.title == title }
            ?: list.firstOrNull { it.title == title }
        return match?.actionIntent != null && match.hasRemoteInput
    }

    fun getReplyHint(sbnKey: String?, index: Int, title: String): String? {
        if (sbnKey == null) return null
        val list = liveActionsBySbnKey[sbnKey] ?: return null
        val match = list.getOrNull(index)?.takeIf { it.title == title }
            ?: list.firstOrNull { it.title == title }
        return match?.replyHint
    }

    fun wasActionTriggered(sbnKey: String?, title: String): Boolean {
        if (sbnKey == null) return false
        return triggeredActionBySbnKey[sbnKey] == title
    }

    fun triggerAction(context: Context, sbnKey: String?, index: Int, title: String): Boolean {
        if (sbnKey == null) {
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            return false
        }
        val list = liveActionsBySbnKey[sbnKey]
        val match = list?.getOrNull(index)?.takeIf { it.title == title }
            ?: list?.firstOrNull { it.title == title }
        val pendingIntent = match?.actionIntent
        if (pendingIntent == null) {
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            triggeredActionBySbnKey[sbnKey] = title
            _versionFlow.value = _versionFlow.value + 1L
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
            SpeyeLogger.d(TAG, "Triggered notification action '$title' for $sbnKey")
            true
        } catch (e: PendingIntent.CanceledException) {
            SpeyeLogger.w(TAG, "Action PendingIntent was canceled for '$title' ($sbnKey)")
            triggeredActionBySbnKey.remove(sbnKey)
            liveActionsBySbnKey.remove(sbnKey)
            _versionFlow.value = _versionFlow.value + 1L
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Failed to send PendingIntent for action '$title'", e)
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun triggerReplyAction(
        context: Context,
        sbnKey: String?,
        index: Int,
        title: String,
        replyText: String
    ): Boolean {
        if (sbnKey == null) {
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            return false
        }
        val list = liveActionsBySbnKey[sbnKey]
        val match = list?.getOrNull(index)?.takeIf { it.title == title }
            ?: list?.firstOrNull { it.title == title }
        val pendingIntent = match?.actionIntent
        val remoteInputs = match?.remoteInputs
        if (pendingIntent == null || remoteInputs.isNullOrEmpty()) {
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val fillInIntent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val resultsBundle = Bundle()
            for (ri in remoteInputs) {
                resultsBundle.putCharSequence(ri.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                RemoteInput.setResultsSource(fillInIntent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            }

            triggeredActionBySbnKey[sbnKey] = title
            _versionFlow.value = _versionFlow.value + 1L

            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pendingIntent.send(context, 0, fillInIntent, null, null, null, options.toBundle())
            } else {
                pendingIntent.send(context, 0, fillInIntent)
            }
            SpeyeLogger.d(TAG, "Triggered reply action '$title' for $sbnKey")
            true
        } catch (e: PendingIntent.CanceledException) {
            SpeyeLogger.w(TAG, "Reply PendingIntent was canceled for '$title' ($sbnKey)")
            triggeredActionBySbnKey.remove(sbnKey)
            liveActionsBySbnKey.remove(sbnKey)
            _versionFlow.value = _versionFlow.value + 1L
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Failed to send reply PendingIntent for action '$title'", e)
            Toast.makeText(context, R.string.action_expired, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
