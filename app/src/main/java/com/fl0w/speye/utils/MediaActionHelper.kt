package com.fl0w.speye.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.fl0w.speye.service.NotificationService

object MediaActionHelper {

    fun getActiveController(context: Context, packageName: String): MediaController? {
        return try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val sessions = sessionManager?.getActiveSessions(ComponentName(context, NotificationService::class.java))
            sessions?.firstOrNull { it.packageName == packageName }
        } catch (e: Exception) {
            SpeyeLogger.e("MediaActionHelper", "Failed to get active controller", e)
            null
        }
    }

    fun togglePlayPause(context: Context, packageName: String): Boolean {
        val controller = getActiveController(context, packageName) ?: return false
        return try {
            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            true
        } catch (e: Exception) {
            SpeyeLogger.e("MediaActionHelper", "Failed to toggle play/pause", e)
            false
        }
    }

    fun continuePlayback(context: Context, packageName: String, positionMs: Long?): Boolean {
        val controller = getActiveController(context, packageName)
        if (controller != null) {
            try {
                if (positionMs != null && positionMs > 0) {
                    controller.transportControls.seekTo(positionMs)
                }
                controller.transportControls.play()
                val sessionActivity = controller.sessionActivity
                if (sessionActivity != null) {
                    sessionActivity.send()
                    return true
                }
            } catch (e: Exception) {
                SpeyeLogger.e("MediaActionHelper", "Failed to continue playback via controller", e)
            }
        }

        // Fallback: Launch app
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            SpeyeLogger.e("MediaActionHelper", "Failed to launch package", e)
            false
        }
    }
}
