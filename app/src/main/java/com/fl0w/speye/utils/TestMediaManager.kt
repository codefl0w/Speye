package com.fl0w.speye.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import com.fl0w.speye.MainActivity

object TestMediaManager {
    private const val NOTIFICATION_ID = 7777
    private const val CHANNEL_ID = "test_media_channel"

    private var mediaSession: MediaSession? = null
    private var mediaPlayer: MediaPlayer? = null
    private var testBitmap: Bitmap? = null

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun playOrToggle(context: Context) {
        if (mediaSession != null) {
            if (mediaPlayer?.isPlaying == true) {
                pause(context)
            } else {
                play(context)
            }
            return
        }
        startPlayback(context)
    }

    fun startPlayback(context: Context) {
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm)

        try {
            val soundUri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(appContext, soundUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            SpeyeLogger.e("TestMediaManager", "Failed to start real MediaPlayer, using simulated audio", e)
        }

        mediaSession?.release()
        val session = MediaSession(appContext, "SpeyeTestMediaSession").apply {
            val sessionIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingSessionIntent = PendingIntent.getActivity(
                appContext,
                0,
                sessionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSessionActivity(pendingSessionIntent)

            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    play(appContext)
                }

                override fun onPause() {
                    pause(appContext)
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(appContext, pos)
                }

                override fun onStop() {
                    stop(appContext)
                }
            })

            val durationMs = mediaPlayer?.duration?.takeIf { it > 0 }?.toLong() ?: 230_000L
            val art = getOrCreateBitmap()

            setMetadata(
                android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Starboy")
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "The Weeknd")
                    .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "Starboy")
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, durationMs)
                    .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .build()
            )

            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
                    )
                    .build()
            )

            isActive = true
        }
        mediaSession = session

        updateNotification(appContext, isPlaying = true, positionMs = 0L)
    }

    fun play(context: Context) {
        val appContext = context.applicationContext
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            SpeyeLogger.e("TestMediaManager", "Error resuming mediaPlayer", e)
        }
        val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
        updatePlaybackState(PlaybackState.STATE_PLAYING, pos)
        updateNotification(appContext, isPlaying = true, positionMs = pos)
    }

    fun pause(context: Context) {
        val appContext = context.applicationContext
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            SpeyeLogger.e("TestMediaManager", "Error pausing mediaPlayer", e)
        }
        val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
        updatePlaybackState(PlaybackState.STATE_PAUSED, pos)
        updateNotification(appContext, isPlaying = false, positionMs = pos)
    }

    fun seekTo(context: Context, pos: Long) {
        val appContext = context.applicationContext
        try {
            mediaPlayer?.seekTo(pos.toInt())
        } catch (e: Exception) {
            SpeyeLogger.e("TestMediaManager", "Error seeking mediaPlayer", e)
        }
        val isPlaying = mediaPlayer?.isPlaying == true
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        updatePlaybackState(state, pos)
        updateNotification(appContext, isPlaying = isPlaying, positionMs = pos)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            SpeyeLogger.e("TestMediaManager", "Error stopping mediaPlayer", e)
        }
        mediaPlayer = null

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun updatePlaybackState(state: Int, positionMs: Long) {
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, positionMs, 1.0f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP
                )
                .build()
        )
    }

    private fun updateNotification(context: Context, isPlaying: Boolean, positionMs: Long) {
        val session = mediaSession ?: return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle("Starboy")
            .setContentText("The Weeknd")
            .setSubText("Starboy")
            .setContentIntent(contentIntent)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Test Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Speye Test Media Playback"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun getOrCreateBitmap(): Bitmap {
        testBitmap?.let { if (!it.isRecycled) return it }
        val b = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val paint = Paint().apply {
            color = Color.rgb(30, 30, 40)
        }
        canvas.drawRect(0f, 0f, 400f, 400f, paint)
        paint.color = Color.rgb(255, 143, 245)
        canvas.drawCircle(200f, 200f, 120f, paint)
        paint.color = Color.BLACK
        paint.textSize = 50f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("SPEYE", 200f, 218f, paint)
        testBitmap = b
        return b
    }
}
