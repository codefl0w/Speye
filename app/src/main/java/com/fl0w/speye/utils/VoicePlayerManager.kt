package com.fl0w.speye.utils

import android.content.Context
import android.media.MediaPlayer
import com.fl0w.speye.data.db.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class VoicePlaybackState(
    val notificationId: Long = 0L,
    val audioPath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

object VoicePlayerManager {
    private const val TAG = "VoicePlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow<VoicePlaybackState?>(null)
    val playbackState: StateFlow<VoicePlaybackState?> = _playbackState.asStateFlow()

    fun togglePlayPause(
        context: Context,
        notificationId: Long,
        audioPath: String?,
        savedPositionMs: Long = 0L,
        fallbackDurationMs: Long = 0L
    ) {
        val currentState = _playbackState.value
        if (currentState != null && currentState.notificationId == notificationId && currentState.isPlaying) {
            pause(context, notificationId)
        } else {
            play(context, notificationId, audioPath, savedPositionMs, fallbackDurationMs)
        }
    }

    fun play(
        context: Context,
        notificationId: Long,
        audioPath: String?,
        savedPositionMs: Long = 0L,
        fallbackDurationMs: Long = 0L
    ) {
        if (audioPath.isNullOrBlank()) {
            SpeyeLogger.w(TAG, "Cannot play voice message: empty audioPath")
            return
        }

        val file = File(audioPath)
        if (!file.exists() || !file.isFile) {
            SpeyeLogger.w(TAG, "Cannot play voice message: file does not exist: $audioPath")
            return
        }

        val currentState = _playbackState.value
        // Resume if same notification was paused
        if (currentState != null && currentState.notificationId == notificationId && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _playbackState.value = currentState.copy(isPlaying = true)
                startProgressTracker(context, notificationId)
                return
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Failed to resume playback, reinitializing", e)
                releasePlayer()
            }
        }

        // Switch track or start fresh
        releasePlayer()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    val totalDuration = if (mp.duration > 0) mp.duration.toLong() else fallbackDurationMs
                    val startPos = if (savedPositionMs in 1 until totalDuration) savedPositionMs.toInt() else 0
                    if (startPos > 0) {
                        mp.seekTo(startPos)
                    }
                    mp.start()
                    _playbackState.value = VoicePlaybackState(
                        notificationId = notificationId,
                        audioPath = audioPath,
                        isPlaying = true,
                        positionMs = startPos.toLong(),
                        durationMs = totalDuration
                    )
                    startProgressTracker(context, notificationId)
                }
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value?.copy(
                        isPlaying = false,
                        positionMs = 0L
                    )
                    stopProgressTracker()
                    persistPosition(context, notificationId, 0L)
                }
                setOnErrorListener { _, what, extra ->
                    SpeyeLogger.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    releasePlayer()
                    _playbackState.value = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Error initializing MediaPlayer for $audioPath", e)
            releasePlayer()
        }
    }

    fun pause(context: Context, notificationId: Long) {
        val currentState = _playbackState.value ?: return
        if (currentState.notificationId != notificationId) return

        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
                val currentPos = player.currentPosition.toLong()
                _playbackState.value = currentState.copy(isPlaying = false, positionMs = currentPos)
                stopProgressTracker()
                persistPosition(context, notificationId, currentPos)
            }
        } catch (e: Exception) {
            SpeyeLogger.e(TAG, "Error pausing MediaPlayer", e)
        }
    }

    fun seekTo(context: Context, notificationId: Long, targetMs: Long) {
        val currentState = _playbackState.value
        if (currentState != null && currentState.notificationId == notificationId && mediaPlayer != null) {
            try {
                mediaPlayer?.seekTo(targetMs.toInt())
                _playbackState.value = currentState.copy(positionMs = targetMs)
                persistPosition(context, notificationId, targetMs)
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Error seeking MediaPlayer", e)
            }
        } else {
            // Seek when stopped/paused
            _playbackState.value = currentState?.takeIf { it.notificationId == notificationId }?.copy(positionMs = targetMs)
            persistPosition(context, notificationId, targetMs)
        }
    }

    fun stopAndRelease(context: Context? = null) {
        val currentState = _playbackState.value
        if (currentState != null && context != null) {
            persistPosition(context, currentState.notificationId, currentState.positionMs)
        }
        releasePlayer()
        _playbackState.value = null
    }

    private fun startProgressTracker(context: Context, notificationId: Long) {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        val currentPos = player.currentPosition.toLong()
                        val duration = if (player.duration > 0) player.duration.toLong() else (_playbackState.value?.durationMs ?: 0L)
                        _playbackState.value = _playbackState.value?.copy(
                            positionMs = currentPos,
                            durationMs = duration
                        )
                    }
                } catch (_: Exception) {}
                delay(100L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        stopProgressTracker()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            SpeyeLogger.w(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    private fun persistPosition(context: Context, notificationId: Long, positionMs: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context.applicationContext).notificationDao()
                dao.updateMediaPosition(notificationId, positionMs)
            } catch (e: Exception) {
                SpeyeLogger.e(TAG, "Failed to persist position for $notificationId", e)
            }
        }
    }
}
