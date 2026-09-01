package com.fl0w.speye.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fl0w.speye.data.model.IgnoredAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun ignoreApp(app: IgnoredAppEntity)

    @Delete
    suspend fun unignoreApp(app: IgnoredAppEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM ignored_apps WHERE packageName = :packageName)")
    suspend fun isIgnored(packageName: String): Boolean

    @Query("SELECT * FROM ignored_apps")
    fun getAllIgnoredApps(): Flow<List<IgnoredAppEntity>>
}
