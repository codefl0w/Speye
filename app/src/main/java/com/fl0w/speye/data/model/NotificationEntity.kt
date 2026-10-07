package com.fl0w.speye.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "notifications",
    indices = [
        Index("sbnKey"),
        Index("packageName"),
        Index("timestamp")
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sbnKey: String? = null,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val appName: String? = null,
    val isSystemRemoved: Boolean = false,
    val imagePath: String? = null,
    val contentIntentUri: String? = null,
    val progress: Int? = null,
    val progressMax: Int? = null,
    val isIndeterminate: Boolean? = null,
    val isMedia: Boolean = false,
    val mediaTitle: String? = null,
    val mediaArtist: String? = null,
    val mediaAlbum: String? = null,
    val mediaDurationMs: Long? = null,
    val mediaPositionMs: Long? = null,
    val mediaPlaybackState: Int? = null,
    val audioPath: String? = null,
    val isVoiceMessage: Boolean = false,
    val voiceSender: String? = null
)
