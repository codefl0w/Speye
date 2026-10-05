package com.fl0w.speye.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fl0w.speye.data.model.IgnoredAppEntity
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationHistoryEntity

@Database(entities = [NotificationEntity::class, NotificationHistoryEntity::class, IgnoredAppEntity::class], version = 7, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun ignoredAppDao(): IgnoredAppDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN progress INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN progressMax INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN isIndeterminate INTEGER DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "speye_database"
                )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
