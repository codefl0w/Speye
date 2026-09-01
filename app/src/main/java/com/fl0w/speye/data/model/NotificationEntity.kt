package com.fl0w.speye.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val contentIntentUri: String? = null
)
