package com.fl0w.speye.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "ignored_apps")
data class IgnoredAppEntity(
    @PrimaryKey val packageName: String,
    val allowText: Boolean = false,
    val allowLazyAssets: Boolean = false,
    val allowStickers: Boolean = false,
    val allowMedia: Boolean = false,
    val allowVoice: Boolean = false,
    val allowProgress: Boolean = false,
    val allowActions: Boolean = false
) {
    val isAllOff: Boolean
        get() = !allowText && !allowLazyAssets && !allowStickers && !allowMedia && !allowVoice && !allowProgress && !allowActions

    val isAllOn: Boolean
        get() = allowText && allowLazyAssets && allowStickers && allowMedia && allowVoice && allowProgress && allowActions

    val isMainToggleOn: Boolean
        get() = !isAllOff

    companion object {
        fun allOn(packageName: String) = IgnoredAppEntity(
            packageName = packageName,
            allowText = true,
            allowLazyAssets = true,
            allowStickers = true,
            allowMedia = true,
            allowVoice = true,
            allowProgress = true,
            allowActions = true
        )

        fun allOff(packageName: String) = IgnoredAppEntity(
            packageName = packageName,
            allowText = false,
            allowLazyAssets = false,
            allowStickers = false,
            allowMedia = false,
            allowVoice = false,
            allowProgress = false,
            allowActions = false
        )
    }
}
