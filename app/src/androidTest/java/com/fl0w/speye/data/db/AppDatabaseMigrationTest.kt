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
    fun migrate6To7_preservesExistingDataAndValidatesRoomSchema() = runBlocking {
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

        // 3. Open via Room with MIGRATION_6_7 registered
        // Room will run MIGRATION_6_7 and perform full schema validation against v7 Room entity definitions
        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .build()

        val dao = migratedRoomDb.notificationDao()
        val allWithHistory = dao.getAllNotificationsWithHistoryList()
        val notificationWithHistory = allWithHistory.firstOrNull { it.notification.id == 101L }

        // 4. Verify data survived migration intact and new columns are initialized to null
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

        assertEquals("History entry should survive migration", 1, notificationWithHistory.history.size)
        assertEquals("Download Queued", notificationWithHistory.history[0].oldText)

        // 5. Verify new rows can be written and queried with progress data
        dao.update(notif.copy(progress = 45, progressMax = 100, isIndeterminate = false))
        val updated = dao.getNotificationById(101)!!
        assertEquals(45, updated.progress)
        assertEquals(100, updated.progressMax)
        assertEquals(false, updated.isIndeterminate)

        migratedRoomDb.close()
    }
}
