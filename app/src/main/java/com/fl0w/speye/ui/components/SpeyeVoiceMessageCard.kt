package com.fl0w.speye.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.utils.VoicePlayerManager
import java.io.File

@Composable
fun SpeyeVoiceMessageCard(
    notification: NotificationEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by VoicePlayerManager.playbackState.collectAsState()

    val isCurrentActive = playbackState?.notificationId == notification.id
    val isPlaying = isCurrentActive && (playbackState?.isPlaying == true)
    val totalDurationMs = if (isCurrentActive && (playbackState?.durationMs ?: 0L) > 0L) {
        playbackState!!.durationMs
    } else {
        notification.mediaDurationMs ?: 0L
    }
    val currentPositionMs = if (isCurrentActive) {
        playbackState?.positionMs ?: 0L
    } else {
        notification.mediaPositionMs ?: 0L
    }

    val senderName = notification.voiceSender?.takeIf { it.isNotBlank() }
        ?: notification.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.voice_message)

    val progressFraction = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        label = "voice_progress"
    )

    val audioFileExists = remember(notification.audioPath) {
        notification.audioPath != null && File(notification.audioPath).let { it.exists() && it.isFile }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpeyeTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Line 1: SENDER with subtle mic icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = SpeyeTheme.colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = senderName,
                color = SpeyeTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Line 2: 0:00 ///////------- 0:17 [Play/Pause]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current time
            Text(
                text = formatDuration(currentPositionMs),
                fontSize = 11.sp,
                color = SpeyeTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Progress bar with leading ball scrubber
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .pointerInput(totalDurationMs, audioFileExists) {
                        detectTapGestures { offset ->
                            if (!audioFileExists) {
                                Toast.makeText(context, R.string.audio_file_not_found, Toast.LENGTH_SHORT).show()
                                return@detectTapGestures
                            }
                            if (totalDurationMs > 0 && size.width > 0) {
                                val seekFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val seekTargetMs = (seekFraction * totalDurationMs).toLong()
                                VoicePlayerManager.seekTo(context, notification.id, seekTargetMs)
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                val thumbSize = 10.dp
                val trackHeight = 4.dp
                val clampedProgress = animatedProgress.coerceIn(0f, 1f)

                // Background Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SpeyeTheme.colors.divider)
                )

                // Played Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clampedProgress)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SpeyeTheme.colors.primary)
                )

                // Leading Ball Scrubber
                Box(
                    modifier = Modifier
                        .offset(x = (maxWidth - thumbSize) * clampedProgress)
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(SpeyeTheme.colors.primary)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Total time
            Text(
                text = formatDuration(totalDurationMs),
                fontSize = 11.sp,
                color = SpeyeTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Play / Pause Icon Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SpeyeTheme.colors.primary.copy(alpha = 0.15f))
                    .clickable {
                        if (!audioFileExists) {
                            Toast.makeText(context, R.string.audio_file_not_found, Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        VoicePlayerManager.togglePlayPause(
                            context = context,
                            notificationId = notification.id,
                            audioPath = notification.audioPath,
                            savedPositionMs = currentPositionMs,
                            fallbackDurationMs = totalDurationMs
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SpeyeTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
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
