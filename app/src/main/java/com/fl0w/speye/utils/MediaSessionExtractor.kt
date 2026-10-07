package com.fl0w.speye.utils

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.fl0w.speye.service.NotificationService

data class MediaSnapshot(
    val isMedia: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val playbackState: Int? = null,
    val coverBitmap: Bitmap? = null,
    val sessionToken: MediaSession.Token? = null
)

object MediaSessionExtractor {

    fun extract(context: Context, sbn: StatusBarNotification): MediaSnapshot {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle()

        // 1. Check for token in extras
        val token = extractToken(extras)

        // 2. Check template
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        val isMediaStyle = template?.contains("MediaStyle", ignoreCase = true) == true

        // 3. Obtain MediaController if token exists or query MediaSessionManager
        var controller: MediaController? = null
        if (token != null) {
            try {
                controller = MediaController(context, token)
            } catch (e: Exception) {
                SpeyeLogger.e("MediaExtractor", "Failed to create MediaController from token", e)
            }
        }

        if (controller == null && isMediaStyle) {
            controller = findActiveControllerForPackage(context, sbn.packageName)
        }

        if (controller == null && token == null && !isMediaStyle) {
            return MediaSnapshot(isMedia = false)
        }

        // 4. Extract from MediaController
        val metadata = controller?.metadata
        val playbackState = controller?.playbackState

        val mediaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

        val mediaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val mediaAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
            ?: (if (extras.containsKey(Notification.EXTRA_PROGRESS_MAX)) extras.getInt(Notification.EXTRA_PROGRESS_MAX).toLong().takeIf { it > 0 } else null)

        val positionMs = playbackState?.position?.takeIf { it >= 0 }
            ?: (if (extras.containsKey(Notification.EXTRA_PROGRESS)) extras.getInt(Notification.EXTRA_PROGRESS).toLong().takeIf { it >= 0 } else null)

        val stateInt = playbackState?.state ?: inferPlaybackStateFromActions(notification)

        val coverBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: extractCoverFromNotification(context, notification, extras)

        return MediaSnapshot(
            isMedia = true,
            title = mediaTitle?.trim()?.takeIf { it.isNotEmpty() },
            artist = mediaArtist?.trim()?.takeIf { it.isNotEmpty() },
            album = mediaAlbum?.trim()?.takeIf { it.isNotEmpty() },
            durationMs = durationMs,
            positionMs = positionMs,
            playbackState = stateInt,
            coverBitmap = coverBitmap?.let { ImageUtils.scaleBitmapIfNeeded(it) },
            sessionToken = token ?: controller?.sessionToken
        )
    }

    private fun extractToken(extras: Bundle): MediaSession.Token? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findActiveControllerForPackage(context: Context, packageName: String): MediaController? {
        return try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val sessions = sessionManager?.getActiveSessions(ComponentName(context, NotificationService::class.java))
            sessions?.firstOrNull { it.packageName == packageName }
        } catch (e: Exception) {
            SpeyeLogger.e("MediaExtractor", "Failed to query getActiveSessions", e)
            null
        }
    }

    private fun inferPlaybackStateFromActions(notification: Notification): Int? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val titleStr = action.title?.toString()?.lowercase() ?: ""
            if (titleStr.contains("pause")) return PlaybackState.STATE_PLAYING
            if (titleStr.contains("play")) return PlaybackState.STATE_PAUSED
        }
        return null
    }

    private fun extractCoverFromNotification(context: Context, notification: Notification, extras: Bundle): Bitmap? {
        return try {
            @Suppress("DEPRECATION")
            val raw = extras.get(Notification.EXTRA_LARGE_ICON)
            when (raw) {
                is Bitmap -> raw
                is Icon -> raw.loadDrawable(context)?.toBitmap()
                else -> notification.getLargeIcon()?.loadDrawable(context)?.toBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
}
