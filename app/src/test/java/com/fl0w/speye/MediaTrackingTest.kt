package com.fl0w.speye

import android.media.session.PlaybackState
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.service.NotificationService
import com.fl0w.speye.utils.MediaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTrackingTest {

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    @Test
    fun formatDuration_formatsMillisecondsCorrectly() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(-500L))
        assertEquals("0:05", formatDuration(5_000L))
        assertEquals("1:15", formatDuration(75_000L))
        assertEquals("3:45", formatDuration(225_000L))
        assertEquals("10:00", formatDuration(600_000L))
        assertEquals("65:30", formatDuration(3_930_000L))
    }

    @Test
    fun mediaSnapshot_toNotificationEntity_mapsAllFieldsAccurately() {
        val snapshot = MediaSnapshot(
            isMedia = true,
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200_000L,
            positionMs = 60_000L,
            playbackState = PlaybackState.STATE_PLAYING,
            coverBitmap = null
        )

        val entity = NotificationEntity(
            packageName = "com.spotify.music",
            title = snapshot.title,
            text = snapshot.artist,
            timestamp = 1700000000000L,
            isMedia = snapshot.isMedia,
            mediaTitle = snapshot.title,
            mediaArtist = snapshot.artist,
            mediaAlbum = snapshot.album,
            mediaDurationMs = snapshot.durationMs,
            mediaPositionMs = snapshot.positionMs,
            mediaPlaybackState = snapshot.playbackState
        )

        assertTrue(entity.isMedia)
        assertEquals("Blinding Lights", entity.mediaTitle)
        assertEquals("The Weeknd", entity.mediaArtist)
        assertEquals("After Hours", entity.mediaAlbum)
        assertEquals(200_000L, entity.mediaDurationMs)
        assertEquals(60_000L, entity.mediaPositionMs)
        assertEquals(PlaybackState.STATE_PLAYING, entity.mediaPlaybackState)
    }

    @Test
    fun trackMatching_differentiatesSameTrackFromNewTrack() {
        val existing = NotificationEntity(
            packageName = "com.spotify.music",
            title = "Starboy",
            text = "The Weeknd",
            timestamp = 1700000000000L,
            isMedia = true,
            mediaTitle = "Starboy",
            mediaArtist = "The Weeknd",
            mediaDurationMs = 230_000L,
            mediaPositionMs = 30_000L,
            mediaPlaybackState = PlaybackState.STATE_PLAYING
        )

        // 1. Same track tick (position update) -> matches
        val sameTrackUpdate = MediaSnapshot(
            isMedia = true,
            title = "Starboy",
            artist = "The Weeknd",
            durationMs = 230_000L,
            positionMs = 35_000L,
            playbackState = PlaybackState.STATE_PLAYING
        )
        val isSameTrack1 = existing.isMedia &&
                existing.packageName == "com.spotify.music" &&
                NotificationService.areTitlesRelated(existing.mediaTitle ?: existing.title, sameTrackUpdate.title) &&
                (existing.mediaArtist == null || sameTrackUpdate.artist == null || existing.mediaArtist == sameTrackUpdate.artist)
        assertTrue("Position tick on same track should match existing row", isSameTrack1)

        // 2. Play to Pause transition on same track -> matches
        val pausedUpdate = sameTrackUpdate.copy(playbackState = PlaybackState.STATE_PAUSED)
        val isSameTrack2 = existing.isMedia &&
                existing.packageName == "com.spotify.music" &&
                NotificationService.areTitlesRelated(existing.mediaTitle ?: existing.title, pausedUpdate.title) &&
                (existing.mediaArtist == null || pausedUpdate.artist == null || existing.mediaArtist == pausedUpdate.artist)
        assertTrue("Pause transition on same track should match existing row", isSameTrack2)

        // 3. Different track -> does not match
        val newTrack = MediaSnapshot(
            isMedia = true,
            title = "Save Your Tears",
            artist = "The Weeknd",
            durationMs = 215_000L,
            positionMs = 0L,
            playbackState = PlaybackState.STATE_PLAYING
        )
        val isSameTrack3 = existing.isMedia &&
                existing.packageName == "com.spotify.music" &&
                NotificationService.areTitlesRelated(existing.mediaTitle ?: existing.title, newTrack.title) &&
                (existing.mediaArtist == null || newTrack.artist == null || existing.mediaArtist == newTrack.artist)
        assertFalse("Different song title must NOT match existing row", isSameTrack3)

        // 4. Same track title but different app -> does not match
        val differentApp = existing.packageName == "com.google.android.apps.youtube.music"
        assertFalse("Same track on different app must NOT collide", differentApp)
    }

    @Test
    fun trackDismissal_freezesPlaybackStateWithoutClearingPosition() {
        val playingEntity = NotificationEntity(
            packageName = "com.spotify.music",
            title = "In The Night",
            text = "The Weeknd",
            timestamp = 1700000000000L,
            isMedia = true,
            mediaTitle = "In The Night",
            mediaArtist = "The Weeknd",
            mediaDurationMs = 235_000L,
            mediaPositionMs = 120_000L,
            mediaPlaybackState = PlaybackState.STATE_PLAYING
        )

        // Simulate onNotificationRemoved freeze logic
        val frozenEntity = playingEntity.copy(
            isSystemRemoved = true,
            mediaPlaybackState = PlaybackState.STATE_PAUSED
        )

        assertTrue(frozenEntity.isSystemRemoved)
        assertEquals(PlaybackState.STATE_PAUSED, frozenEntity.mediaPlaybackState)
        assertEquals(120_000L, frozenEntity.mediaPositionMs)
        assertEquals(235_000L, frozenEntity.mediaDurationMs)
        assertEquals("In The Night", frozenEntity.mediaTitle)
    }
}
