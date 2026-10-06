package com.fl0w.speye.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "notification_history",
    foreignKeys = [
        ForeignKey(
            entity = NotificationEntity::class,
            parentColumns = ["id"],
            childColumns = ["notificationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notificationId")]
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationId: Long,
    val oldText: String?,
    val timestamp: Long
)
