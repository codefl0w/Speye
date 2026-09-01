package com.fl0w.speye.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationHistoryEntity
import com.fl0w.speye.data.model.NotificationWithHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Update
    suspend fun update(notification: NotificationEntity)

    @Insert
    suspend fun insertHistory(history: NotificationHistoryEntity)

    @Transaction
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsWithHistory(): Flow<List<NotificationWithHistory>>

    @Transaction
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllNotificationsWithHistoryList(): List<NotificationWithHistory>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :packageName")
    suspend fun getNotificationsByPackageName(packageName: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications")
    suspend fun getAllNotifications(): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Query("SELECT * FROM notifications WHERE sbnKey = :key LIMIT 1")
    suspend fun getNotificationBySbnKey(key: String): NotificationEntity?

    @Query("UPDATE notifications SET isSystemRemoved = 1 WHERE sbnKey = :key")
    suspend fun markAsSystemRemoved(key: String)

    @Query("SELECT imagePath FROM notifications WHERE id = :id AND imagePath IS NOT NULL")
    suspend fun getImagePathById(id: Long): String?

    @Query("SELECT imagePath FROM notifications WHERE packageName = :packageName AND imagePath IS NOT NULL")
    suspend fun getImagePathsByPackageName(packageName: String): List<String>

    @Query("SELECT imagePath FROM notifications WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Query("SELECT imagePath FROM notifications WHERE timestamp < :cutoffTimestamp AND imagePath IS NOT NULL")
    suspend fun getImagePathsOlderThan(cutoffTimestamp: Long): List<String>

    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
