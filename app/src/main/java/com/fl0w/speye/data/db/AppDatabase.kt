package com.fl0w.speye.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fl0w.speye.data.model.IgnoredAppEntity
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationHistoryEntity

@Database(entities = [NotificationEntity::class, NotificationHistoryEntity::class, IgnoredAppEntity::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun ignoredAppDao(): IgnoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "speye_database"
                )
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
