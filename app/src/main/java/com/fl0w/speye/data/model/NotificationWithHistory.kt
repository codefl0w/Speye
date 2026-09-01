package com.fl0w.speye.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class NotificationWithHistory(
    @Embedded val notification: NotificationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "notificationId"
    )
    val history: List<NotificationHistoryEntity>
)
