package com.fl0w.speye.ui.components

import android.media.session.PlaybackState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.utils.MediaActionHelper
import java.io.File

@Composable
fun SpeyeMediaCard(
    notification: NotificationEntity,
    modifier: Modifier = Modifier,
    onCoverClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isPlaying = notification.mediaPlaybackState == PlaybackState.STATE_PLAYING && !notification.isSystemRemoved
    val durationMs = notification.mediaDurationMs ?: 0L
    val positionMs = notification.mediaPositionMs ?: 0L

    val trackTitle = notification.mediaTitle ?: notification.title ?: "Unknown Track"
    val artistName = notification.mediaArtist ?: notification.text ?: "Unknown Artist"

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
                            color = SpeyeTheme.colors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
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
                fontSize = 11.sp,
                color = SpeyeTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDuration(durationMs),
                fontSize = 11.sp,
                color = SpeyeTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium
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
                        MediaActionHelper.togglePlayPause(context, notification.packageName)
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
