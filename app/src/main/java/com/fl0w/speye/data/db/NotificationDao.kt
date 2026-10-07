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

    @Query("SELECT * FROM notifications WHERE sbnKey = :key AND timestamp = :timestamp LIMIT 1")
    suspend fun getNotificationBySbnKeyAndTimestamp(key: String, timestamp: Long): NotificationEntity?

    @Query("""
        SELECT * FROM notifications 
        WHERE packageName = :packageName 
          AND timestamp = :timestamp 
          AND ((title IS NULL AND :title IS NULL) OR title = :title) 
          AND ((text IS NULL AND :text IS NULL) OR text = :text) 
        LIMIT 1
    """)
    suspend fun findDuplicate(
        packageName: String,
        timestamp: Long,
        title: String?,
        text: String?
    ): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE sbnKey = :key AND isSystemRemoved = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveNotificationBySbnKey(key: String): NotificationEntity?

    @Query("UPDATE notifications SET isSystemRemoved = 1 WHERE sbnKey = :key AND isSystemRemoved = 0")
    suspend fun markAsSystemRemoved(key: String)

    @Query("UPDATE notifications SET isSystemRemoved = 1 WHERE id = :id")
    suspend fun markAsSystemRemovedById(id: Long)

    @Query("SELECT imagePath FROM notifications WHERE id = :id AND imagePath IS NOT NULL")
    suspend fun getImagePathById(id: Long): String?

    @Query("SELECT imagePath FROM notifications WHERE packageName = :packageName AND imagePath IS NOT NULL")
    suspend fun getImagePathsByPackageName(packageName: String): List<String>

    @Query("SELECT imagePath FROM notifications WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Query("SELECT imagePath FROM notifications WHERE timestamp < :cutoffTimestamp AND imagePath IS NOT NULL")
    suspend fun getImagePathsOlderThan(cutoffTimestamp: Long): List<String>

    @Query("SELECT COUNT(*) FROM notifications WHERE imagePath = :imagePath")
    suspend fun countNotificationsUsingImage(imagePath: String): Int

    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM notification_history WHERE id = :historyId")
    suspend fun deleteHistoryById(historyId: Long): Int

    @Query("SELECT COUNT(*) FROM notification_history WHERE notificationId = :notificationId")
    suspend fun getHistoryCount(notificationId: Long): Int

    @Query("UPDATE notifications SET mediaPositionMs = :positionMs WHERE id = :id")
    suspend fun updateMediaPosition(id: Long, positionMs: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE audioPath = :audioPath")
    suspend fun countNotificationsUsingAudio(audioPath: String): Int

    @Query("SELECT audioPath FROM notifications WHERE audioPath IS NOT NULL")
    suspend fun getAllAudioPaths(): List<String>
}

