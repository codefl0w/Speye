package com.fl0w.speye.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val notifications: List<SerializableNotification>,
    val ignoredApps: List<String> = emptyList(),
    val appFilters: List<SerializableAppFilter> = emptyList()
)

@Serializable
data class SerializableAppFilter(
    val packageName: String,
    val allowText: Boolean = false,
    val allowLazyAssets: Boolean = false,
    val allowStickers: Boolean = false,
    val allowMedia: Boolean = false,
    val allowVoice: Boolean = false,
    val allowProgress: Boolean = false,
    val allowActions: Boolean = false
)

@Serializable
data class SerializableNotification(
    val sbnKey: String?,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val appName: String?,
    val isSystemRemoved: Boolean = false,
    val imagePath: String? = null,
    val history: List<SerializableHistory> = emptyList(),
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
    val voiceSender: String? = null,
    val actionTitles: String? = null
)

@Serializable
data class SerializableHistory(
    val oldText: String?,
    val timestamp: Long,
    val oldActionTitles: String? = null
)
