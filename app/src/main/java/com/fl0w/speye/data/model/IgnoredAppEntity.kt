package com.fl0w.speye.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ignored_apps")
data class IgnoredAppEntity(
    @PrimaryKey val packageName: String
)
