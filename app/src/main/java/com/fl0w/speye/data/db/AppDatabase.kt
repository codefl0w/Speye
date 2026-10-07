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

@Database(entities = [NotificationEntity::class, NotificationHistoryEntity::class, IgnoredAppEntity::class], version = 9, exportSchema = true)
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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN isMedia INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notifications ADD COLUMN mediaTitle TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN mediaArtist TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN mediaAlbum TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN mediaDurationMs INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN mediaPositionMs INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN mediaPlaybackState INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN audioPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE notifications ADD COLUMN isVoiceMessage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notifications ADD COLUMN voiceSender TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }

        val MIGRATION_6_9 = object : Migration(6, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
                MIGRATION_8_9.migrate(db)
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
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_6_8, MIGRATION_8_9, MIGRATION_7_9, MIGRATION_6_9)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
