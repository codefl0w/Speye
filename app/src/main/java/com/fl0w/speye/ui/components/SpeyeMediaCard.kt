package com.fl0w.speye.ui.components

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fl0w.speye.R
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.utils.MediaActionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun SpeyeMediaCard(
    notification: NotificationEntity,
    modifier: Modifier = Modifier,
    onCoverClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    var activeController by remember(notification.packageName, notification.isSystemRemoved) {
        mutableStateOf(
            if (!notification.isSystemRemoved) {
                MediaActionHelper.getActiveController(context, notification.packageName)
            } else null
        )
    }

    var playbackState by remember(activeController) {
        mutableStateOf(activeController?.playbackState)
    }

    var metadata by remember(activeController) {
        mutableStateOf(activeController?.metadata)
    }

    // Keep active controller fresh if media session starts slightly after notification post
    LaunchedEffect(notification.packageName, notification.isSystemRemoved, activeController) {
        if (!notification.isSystemRemoved && activeController == null) {
            var retries = 0
            while (activeController == null && retries < 5) {
                delay(1000L)
                val found = MediaActionHelper.getActiveController(context, notification.packageName)
                if (found != null) {
                    activeController = found
                    playbackState = found.playbackState
                    metadata = found.metadata
                    break
                }
                retries++
            }
        }
    }

    // Register real-time callback on active MediaController
    DisposableEffect(activeController) {
        val controller = activeController ?: return@DisposableEffect onDispose {}
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
            }

            override fun onMetadataChanged(meta: MediaMetadata?) {
                metadata = meta
            }
        }
        controller.registerCallback(callback)
        onDispose {
            controller.unregisterCallback(callback)
        }
    }

    val isPlaying = if (notification.isSystemRemoved) {
        false
    } else {
        (playbackState?.state ?: notification.mediaPlaybackState) == PlaybackState.STATE_PLAYING
    }

    val durationMs = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 })
        ?: notification.mediaDurationMs
        ?: 0L

    fun computeCurrentPosition(): Long {
        val ps = playbackState
        if (ps != null) {
            var pos = ps.position
            if (ps.state == PlaybackState.STATE_PLAYING && ps.lastPositionUpdateTime > 0) {
                val delta = SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime
                if (delta > 0) {
                    pos += (delta * ps.playbackSpeed).toLong()
                }
            }
            return if (durationMs > 0) pos.coerceIn(0L, durationMs) else pos.coerceAtLeast(0L)
        }
        val basePos = notification.mediaPositionMs ?: 0L
        if (isPlaying && notification.timestamp > 0) {
            val delta = System.currentTimeMillis() - notification.timestamp
            val pos = basePos + delta.coerceAtLeast(0L)
            return if (durationMs > 0) pos.coerceIn(0L, durationMs) else pos.coerceAtLeast(0L)
        }
        return if (durationMs > 0) basePos.coerceIn(0L, durationMs) else basePos.coerceAtLeast(0L)
    }

    var positionMs by remember(notification.id, notification.mediaPositionMs) {
        mutableLongStateOf(computeCurrentPosition())
    }

    LaunchedEffect(isPlaying, playbackState, notification.mediaPositionMs) {
        positionMs = computeCurrentPosition()
        if (isPlaying) {
            while (isActive) {
                delay(500L)
                positionMs = computeCurrentPosition()
            }
        }
    }

    val trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: notification.mediaTitle
        ?: notification.title
        ?: stringResource(R.string.unknown_track)
    val artistName = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: notification.mediaArtist
        ?: notification.text
        ?: stringResource(R.string.unknown_artist)

    val imagePath = notification.imagePath
    val coverExists = remember(imagePath) {
        imagePath != null && File(imagePath).let { it.exists() && it.isFile }
    }

    val progressFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = if (SpeyeTheme.reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(300),
        label = "media_progress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpeyeTheme.colors.surface)
            .padding(16.dp)
    ) {
        // Top section: Cover on left, Title/Artist on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpeyeTheme.colors.divider)
                    .clickable(enabled = coverExists && onCoverClick != null) {
                        imagePath?.let { onCoverClick?.invoke(it) }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (coverExists && imagePath != null) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpeyeTheme.colors.textSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artistName,
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                notification.mediaAlbum?.let { album ->
                    if (album.isNotBlank() && album != trackTitle) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = album,
                            color = SpeyeTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Bar with leading ball scrubber
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val thumbSize = 10.dp
            val trackHeight = 4.dp
            val progressClamped = animatedProgress.coerceIn(0f, 1f)

            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SpeyeTheme.colors.divider)
            )

            // Played track
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressClamped)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SpeyeTheme.colors.primary)
            )

            // Scrubber ball on the leading edge of progress
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumbSize) * progressClamped)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(SpeyeTheme.colors.primary)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Timestamps (Current / Total)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(positionMs),
                fontSize = 12.sp,
                color = SpeyeTheme.colors.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatDuration(durationMs),
                fontSize = 12.sp,
                color = SpeyeTheme.colors.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Play / Pause Control Button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SpeyeTheme.colors.primary.copy(alpha = 0.15f))
                    .clickable {
                        if (isPlaying) {
                            MediaActionHelper.togglePlayPause(context, notification.packageName)
                        } else {
                            val resumed = MediaActionHelper.continuePlayback(context, notification.packageName, positionMs)
                            if (!resumed) {
                                MediaActionHelper.togglePlayPause(context, notification.packageName)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SpeyeTheme.colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
