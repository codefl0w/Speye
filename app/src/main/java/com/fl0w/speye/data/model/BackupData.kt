package com.fl0w.speye.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val notifications: List<SerializableNotification>,
    val ignoredApps: List<String>
)

@Serializable
data class SerializableNotification(
    val sbnKey: String?,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val appName: String?,
    val isSystemRemoved: Boolean,
    val imagePath: String?,
    val history: List<SerializableHistory>,
    val contentIntentUri: String? = null
)

@Serializable
data class SerializableHistory(
    val oldText: String?,
    val timestamp: Long
)
