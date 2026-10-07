package com.fl0w.speye.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val dbName = "migration_test.db"
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate6To8_preservesExistingDataAndValidatesRoomSchema() = runBlocking {
        // 1. Create a SQLite database with the exact Room v6 schema
        val openHelperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sbnKey` TEXT, `packageName` TEXT NOT NULL, `title` TEXT, `text` TEXT, `timestamp` INTEGER NOT NULL, `appName` TEXT, `isSystemRemoved` INTEGER NOT NULL, `imagePath` TEXT, `contentIntentUri` TEXT)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_sbnKey` ON `notifications` (`sbnKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_timestamp` ON `notifications` (`timestamp`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `notificationId` INTEGER NOT NULL, `oldText` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`notificationId`) REFERENCES `notifications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_notificationId` ON `notification_history` (`notificationId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `ignored_apps` (`packageName` TEXT NOT NULL, PRIMARY KEY(`packageName`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2950fbbc8246f3b49d0f2c40e04e607e')")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(openHelperConfig)
        val v6Db = helper.writableDatabase

        // 2. Insert sample data into v6 schema
        v6Db.execSQL(
            "INSERT INTO notifications (id, packageName, timestamp, isSystemRemoved, title, text, appName) " +
            "VALUES (101, 'com.test.progress', 1700000000000, 0, 'Download Started', 'Downloading file...', 'TestApp')"
        )
        v6Db.execSQL(
            "INSERT INTO notification_history (id, notificationId, oldText, timestamp) " +
            "VALUES (201, 101, 'Download Queued', 1699999999000)"
        )
        v6Db.close()

        // 3. Open via Room with all migrations registered
        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_6_8,
                AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_7_9, AppDatabase.MIGRATION_6_9
            )
            .build()

        val dao = migratedRoomDb.notificationDao()
        val allWithHistory = dao.getAllNotificationsWithHistoryList()
        val notificationWithHistory = allWithHistory.firstOrNull { it.notification.id == 101L }

        // 4. Verify data survived migration intact and new columns are initialized correctly
        assertTrue("Notification should exist in migrated DB", notificationWithHistory != null)
        val notif = notificationWithHistory!!.notification
        assertEquals(101L, notif.id)
        assertEquals("com.test.progress", notif.packageName)
        assertEquals("Download Started", notif.title)
        assertEquals("Downloading file...", notif.text)
        assertEquals("TestApp", notif.appName)
        assertNull("progress should be null for existing rows", notif.progress)
        assertNull("progressMax should be null for existing rows", notif.progressMax)
        assertNull("isIndeterminate should be null for existing rows", notif.isIndeterminate)
        assertEquals(false, notif.isMedia)
        assertNull("mediaTitle should be null", notif.mediaTitle)
        assertNull("mediaArtist should be null", notif.mediaArtist)
        assertNull("mediaDurationMs should be null", notif.mediaDurationMs)
        assertNull("audioPath should be null", notif.audioPath)
        assertEquals(false, notif.isVoiceMessage)
        assertNull("voiceSender should be null", notif.voiceSender)

        assertEquals("History entry should survive migration", 1, notificationWithHistory.history.size)
        assertEquals("Download Queued", notificationWithHistory.history[0].oldText)

        // 5. Verify new rows can be written and queried with progress and media data
        dao.update(
            notif.copy(
                progress = 45,
                progressMax = 100,
                isIndeterminate = false,
                isMedia = true,
                mediaTitle = "Blinding Lights",
                mediaArtist = "The Weeknd",
                mediaDurationMs = 200000L,
                mediaPositionMs = 50000L,
                mediaPlaybackState = 3,
                audioPath = "/data/user/0/com.fl0w.speye/files/voice/aud_test.ogg",
                isVoiceMessage = true,
                voiceSender = "Alice"
            )
        )
        val updated = dao.getNotificationById(101)!!
        assertEquals(45, updated.progress)
        assertEquals(100, updated.progressMax)
        assertEquals(false, updated.isIndeterminate)
        assertTrue(updated.isMedia)
        assertEquals("Blinding Lights", updated.mediaTitle)
        assertEquals("The Weeknd", updated.mediaArtist)
        assertEquals(200000L, updated.mediaDurationMs)
        assertEquals(50000L, updated.mediaPositionMs)
        assertEquals(3, updated.mediaPlaybackState)
        assertEquals("/data/user/0/com.fl0w.speye/files/voice/aud_test.ogg", updated.audioPath)
        assertTrue(updated.isVoiceMessage)
        assertEquals("Alice", updated.voiceSender)

        migratedRoomDb.close()
    }

    @Test
    fun migrate7To8_preservesExistingDataAndValidatesRoomSchema() = runBlocking {
        // 1. Create a SQLite database with the Room v7 schema
        val openHelperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sbnKey` TEXT, `packageName` TEXT NOT NULL, `title` TEXT, `text` TEXT, `timestamp` INTEGER NOT NULL, `appName` TEXT, `isSystemRemoved` INTEGER NOT NULL, `imagePath` TEXT, `contentIntentUri` TEXT, `progress` INTEGER, `progressMax` INTEGER, `isIndeterminate` INTEGER)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_sbnKey` ON `notifications` (`sbnKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_timestamp` ON `notifications` (`timestamp`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `notificationId` INTEGER NOT NULL, `oldText` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`notificationId`) REFERENCES `notifications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_notificationId` ON `notification_history` (`notificationId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `ignored_apps` (`packageName` TEXT NOT NULL, PRIMARY KEY(`packageName`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a604b88f5e7e6a43887a2f13dec544ce')")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(openHelperConfig)
        val v7Db = helper.writableDatabase

        v7Db.execSQL(
            "INSERT INTO notifications (id, packageName, timestamp, isSystemRemoved, title, text, appName, progress, progressMax, isIndeterminate) " +
            "VALUES (102, 'com.test.v7', 1700000001000, 0, 'v7 Title', 'v7 Text', 'TestApp7', 80, 100, 0)"
        )
        v7Db.close()

        // 2. Open via Room with all migrations registered
        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_6_8,
                AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_7_9, AppDatabase.MIGRATION_6_9
            )
            .build()

        val dao = migratedRoomDb.notificationDao()
        val notif = dao.getNotificationById(102)
        assertTrue("Notification should exist in migrated v9 DB", notif != null)
        assertEquals(80, notif!!.progress)
        assertEquals(false, notif.isMedia)
        assertNull(notif.mediaTitle)
        assertEquals(false, notif.isVoiceMessage)
        assertNull(notif.audioPath)

        migratedRoomDb.close()
    }

    @Test
    fun migrate8To9_preservesExistingDataAndValidatesRoomSchema() = runBlocking {
        // 1. Create a SQLite database with the Room v8 schema
        val openHelperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sbnKey` TEXT, `packageName` TEXT NOT NULL, `title` TEXT, `text` TEXT, `timestamp` INTEGER NOT NULL, `appName` TEXT, `isSystemRemoved` INTEGER NOT NULL, `imagePath` TEXT, `contentIntentUri` TEXT, `progress` INTEGER, `progressMax` INTEGER, `isIndeterminate` INTEGER, `isMedia` INTEGER NOT NULL, `mediaTitle` TEXT, `mediaArtist` TEXT, `mediaAlbum` TEXT, `mediaDurationMs` INTEGER, `mediaPositionMs` INTEGER, `mediaPlaybackState` INTEGER)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_sbnKey` ON `notifications` (`sbnKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_timestamp` ON `notifications` (`timestamp`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `notificationId` INTEGER NOT NULL, `oldText` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`notificationId`) REFERENCES `notifications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_notificationId` ON `notification_history` (`notificationId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `ignored_apps` (`packageName` TEXT NOT NULL, PRIMARY KEY(`packageName`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'db9ba249f7b116fb4d2572b94c64fe96')")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(openHelperConfig)
        val v8Db = helper.writableDatabase

        v8Db.execSQL(
            "INSERT INTO notifications (id, packageName, timestamp, isSystemRemoved, title, text, appName, isMedia, mediaTitle, mediaArtist) " +
            "VALUES (103, 'com.test.v8', 1700000002000, 0, 'v8 Song', 'v8 Artist', 'TestApp8', 1, 'v8 Song', 'v8 Artist')"
        )
        v8Db.close()

        // 2. Open via Room with MIGRATION_8_9
        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_6_8,
                AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_7_9, AppDatabase.MIGRATION_6_9
            )
            .build()

        val dao = migratedRoomDb.notificationDao()
        val notif = dao.getNotificationById(103)
        assertTrue("Notification should exist in migrated v9 DB", notif != null)
        assertTrue(notif!!.isMedia)
        assertEquals("v8 Song", notif.mediaTitle)
        assertEquals(false, notif.isVoiceMessage)
        assertNull(notif.audioPath)
        assertNull(notif.voiceSender)

        // Verify writing new voice message fields
        dao.update(
            notif.copy(
                isVoiceMessage = true,
                voiceSender = "Bob",
                audioPath = "/sdcard/Speye/saved/voice/test.ogg"
            )
        )
        val updated = dao.getNotificationById(103)!!
        assertTrue(updated.isVoiceMessage)
        assertEquals("Bob", updated.voiceSender)
        assertEquals("/sdcard/Speye/saved/voice/test.ogg", updated.audioPath)

        migratedRoomDb.close()
    }
}
