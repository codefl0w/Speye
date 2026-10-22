package com.fl0w.speye.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.fl0w.speye.service.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            return try {
                if (positionMs != null && positionMs > 0) {
                    controller.transportControls.seekTo(positionMs)
                }
                controller.transportControls.play()
                val sessionActivity = controller.sessionActivity
                if (sessionActivity != null) {
                    sessionActivity.send()
                }
                true
            } catch (e: Exception) {
                SpeyeLogger.e("MediaActionHelper", "Failed to continue playback via controller", e)
                false
            }
        }

        // App was killed or has no active session.
        // 1. Send MEDIA_PLAY key events to wake up the app's MediaButtonReceiver
        try {
            val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(packageName)
                putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            context.sendOrderedBroadcast(downIntent, null)
            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(packageName)
                putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            context.sendOrderedBroadcast(upIntent, null)
        } catch (e: Exception) {
            SpeyeLogger.w("MediaActionHelper", "Failed to broadcast media button: ${e.message}")
        }

        // 2. Launch the app package
        val launched = try {
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

        // 3. Asynchronously wait for revived player's MediaSession, then seek to position and play
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var attempts = 0
            while (attempts < 15) { // 15 x 300ms = 4.5s window
                kotlinx.coroutines.delay(300L)
                val newController = getActiveController(context, packageName)
                if (newController != null) {
                    try {
                        if (positionMs != null && positionMs > 0) {
                            newController.transportControls.seekTo(positionMs)
                        }
                        newController.transportControls.play()
                        SpeyeLogger.d("MediaActionHelper", "Resumed playback on revived player: $packageName at $positionMs ms")
                        break
                    } catch (e: Exception) {
                        SpeyeLogger.w("MediaActionHelper", "Retrying resume on revived player: ${e.message}")
                    }
                }
                attempts++
            }
        }

        return launched
    }
}
