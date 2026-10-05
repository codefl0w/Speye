package com.fl0w.speye.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun countNotificationsUsingImage_tracksReferencesAccurately() = runBlocking {
        val imgA = "/data/user/0/com.fl0w.speye/files/img_aaa.png"
        val imgB = "/data/user/0/com.fl0w.speye/files/img_bbb.png"

        assertEquals(0, dao.countNotificationsUsingImage(imgA))

        val id1 = dao.insert(
            NotificationEntity(
                packageName = "app.a",
                timestamp = 1000L,
                title = "A1",
                text = "T1",
                imagePath = imgA
            )
        )
        assertEquals(1, dao.countNotificationsUsingImage(imgA))

        val id2 = dao.insert(
            NotificationEntity(
                packageName = "app.b",
                timestamp = 2000L,
                title = "B1",
                text = "T2",
                imagePath = imgA
            )
        )
        assertEquals(2, dao.countNotificationsUsingImage(imgA))

        val id3 = dao.insert(
            NotificationEntity(
                packageName = "app.c",
                timestamp = 3000L,
                title = "C1",
                text = "T3",
                imagePath = imgB
            )
        )
        assertEquals(2, dao.countNotificationsUsingImage(imgA))
        assertEquals(1, dao.countNotificationsUsingImage(imgB))

        // Delete id1
        dao.deleteById(id1)
        assertEquals(1, dao.countNotificationsUsingImage(imgA))

        // Delete id2
        dao.deleteById(id2)
        assertEquals(0, dao.countNotificationsUsingImage(imgA))
        assertEquals(1, dao.countNotificationsUsingImage(imgB))
    }

    @Test
    fun historyCascadeDelete_deletingParentRemovesAllHistory() = runBlocking {
        val parentId = dao.insert(
            NotificationEntity(
                packageName = "app.chat",
                timestamp = 1000L,
                title = "Alice",
                text = "Latest message"
            )
        )

        dao.insertHistory(NotificationHistoryEntity(notificationId = parentId, oldText = "Message 1", timestamp = 800L))
        dao.insertHistory(NotificationHistoryEntity(notificationId = parentId, oldText = "Message 2", timestamp = 900L))

        val withHistoryBefore = dao.getAllNotificationsWithHistoryList()
        assertEquals(1, withHistoryBefore.size)
        assertEquals(2, withHistoryBefore[0].history.size)
        assertEquals(2, dao.getHistoryCount(parentId))

        dao.deleteById(parentId)

        val withHistoryAfter = dao.getAllNotificationsWithHistoryList()
        assertTrue(withHistoryAfter.isEmpty())
        assertEquals(0, dao.getHistoryCount(parentId))
    }

    @Test
    fun progressBarUpdates_updateSingleRowInPlaceWithoutInsertingNewRows() = runBlocking {
        val sbnKey = "0|com.android.providers.downloads|101|null|1000"

        // Initial progress: 0%
        val rowId = dao.insert(
            NotificationEntity(
                sbnKey = sbnKey,
                packageName = "com.android.providers.downloads",
                timestamp = 1000L,
                title = "Downloading Speye.apk",
                text = "0%",
                progress = 0,
                progressMax = 100,
                isIndeterminate = false
            )
        )
        assertEquals(1, dao.getNotificationCount())

        // Simulated progress update ticks: 25%, 50%, 75%, 100%
        val progressSteps = listOf(25, 50, 75, 100)
        for ((idx, p) in progressSteps.withIndex()) {
            val existing = dao.getActiveNotificationBySbnKey(sbnKey)
            assertTrue("Active row must exist for key", existing != null)
            assertEquals("Must target same row id each time", rowId, existing!!.id)

            // Update in-place
            dao.update(
                existing.copy(
                    text = "$p%",
                    progress = p,
                    timestamp = 1000L + (idx + 1) * 200L
                )
            )

            // Must NOT create new rows
            assertEquals("Table must retain exactly 1 row across all progress updates", 1, dao.getNotificationCount())
        }

        // Verify final state of the single row
        val finalEntity = dao.getNotificationById(rowId)!!
        assertEquals(100, finalEntity.progress)
        assertEquals(100, finalEntity.progressMax)
        assertEquals("100%", finalEntity.text)
        assertEquals(1800L, finalEntity.timestamp)

        // Verify zero history entries spammed
        assertEquals(0, dao.getHistoryCount(rowId))
    }
}

