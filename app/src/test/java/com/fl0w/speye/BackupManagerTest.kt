package com.fl0w.speye

import com.fl0w.speye.data.model.BackupData
import com.fl0w.speye.data.model.SerializableHistory
import com.fl0w.speye.data.model.SerializableNotification
import com.fl0w.speye.utils.BackupManager
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    @Test
    fun writeBackupZipAndExtract_roundTrip_attachmentsAndDataPreserved() {
        val tempDir = Files.createTempDirectory("backup_test").toFile()
        val extractDir = Files.createTempDirectory("backup_extract").toFile()
        try {
            val attachmentFile = File(tempDir, "img_deadbeef.webp")
            attachmentFile.writeBytes("dummy image bytes for test".toByteArray(Charsets.UTF_8))

            val notification = SerializableNotification(
                sbnKey = "0|com.example.chat|1|tag|1000",
                packageName = "com.example.chat",
                title = "Alice",
                text = "Hello there!",
                timestamp = 1700000000123L,
                appName = "ChatApp",
                isSystemRemoved = false,
                imagePath = "img_deadbeef.webp",
                history = listOf(
                    SerializableHistory("Draft message", 1700000000000L),
                    SerializableHistory("Earlier text", 1699999999000L)
                ),
                contentIntentUri = "intent://chat#Intent;scheme=app;end"
            )

            val backupData = BackupData(
                notifications = listOf(notification),
                ignoredApps = listOf("com.system.bloat", "com.spam.ads")
            )

            val outStream = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, listOf(attachmentFile), outStream)
            val zipBytes = outStream.toByteArray()
            assertTrue(zipBytes.isNotEmpty())

            val extracted = BackupManager.extractBackupZip(ByteArrayInputStream(zipBytes), extractDir)

            assertEquals(1, extracted.notifications.size)
            val extractedNotif = extracted.notifications[0]
            assertEquals("0|com.example.chat|1|tag|1000", extractedNotif.sbnKey)
            assertEquals("com.example.chat", extractedNotif.packageName)
            assertEquals("Alice", extractedNotif.title)
            assertEquals("Hello there!", extractedNotif.text)
            assertEquals(1700000000123L, extractedNotif.timestamp)
            assertEquals("ChatApp", extractedNotif.appName)
            assertFalse(extractedNotif.isSystemRemoved)
            assertEquals("img_deadbeef.webp", extractedNotif.imagePath)
            assertEquals("intent://chat#Intent;scheme=app;end", extractedNotif.contentIntentUri)

            assertEquals(2, extractedNotif.history.size)
            assertEquals("Draft message", extractedNotif.history[0].oldText)
            assertEquals(1700000000000L, extractedNotif.history[0].timestamp)
            assertEquals("Earlier text", extractedNotif.history[1].oldText)
            assertEquals(1699999999000L, extractedNotif.history[1].timestamp)

            assertEquals(listOf("com.system.bloat", "com.spam.ads"), extracted.ignoredApps)

            val extractedAttachment = File(extractDir, "attachments/img_deadbeef.webp")
            assertTrue(extractedAttachment.exists())
            assertEquals("dummy image bytes for test", extractedAttachment.readText(Charsets.UTF_8))
        } finally {
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun extractBackupZip_pathTraversalAttack_throwsSecurityException() {
        val extractDir = Files.createTempDirectory("backup_malicious").toFile()
        try {
            val maliciousZip = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zos ->
                    zos.putNextEntry(ZipEntry("../evil.txt"))
                    zos.write("malicious payload".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                baos.toByteArray()
            }

            try {
                BackupManager.extractBackupZip(ByteArrayInputStream(maliciousZip), extractDir)
                fail("Expected SecurityException on path traversal entry")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("Zip entry outside target directory") == true)
            }
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun extractBackupZip_missingDataJson_throwsIOException() {
        val extractDir = Files.createTempDirectory("backup_no_data").toFile()
        try {
            val emptyZip = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zos ->
                    zos.putNextEntry(ZipEntry("attachments/dummy.txt"))
                    zos.write("foo".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                baos.toByteArray()
            }

            try {
                BackupManager.extractBackupZip(ByteArrayInputStream(emptyZip), extractDir)
                fail("Expected IOException when data.json is missing")
            } catch (e: IOException) {
                assertTrue(e.message?.contains("data.json missing") == true)
            }
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun backupData_jsonSerialization_handlesNullAndOptionalFields() {
        val notificationWithNulls = SerializableNotification(
            sbnKey = null,
            packageName = "com.test.minimal",
            title = null,
            text = null,
            timestamp = 123456789L,
            appName = null,
            isSystemRemoved = true,
            imagePath = null,
            history = emptyList(),
            contentIntentUri = null
        )

        val backupData = BackupData(
            notifications = listOf(notificationWithNulls),
            ignoredApps = emptyList()
        )

        val tempDir = Files.createTempDirectory("backup_nulls").toFile()
        val extractDir = Files.createTempDirectory("backup_nulls_extract").toFile()
        try {
            val outStream = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, emptyList(), outStream)
            val extracted = BackupManager.extractBackupZip(ByteArrayInputStream(outStream.toByteArray()), extractDir)

            assertEquals(1, extracted.notifications.size)
            val notif = extracted.notifications[0]
            assertNull(notif.sbnKey)
            assertEquals("com.test.minimal", notif.packageName)
            assertNull(notif.title)
            assertNull(notif.text)
            assertEquals(123456789L, notif.timestamp)
            assertNull(notif.appName)
            assertTrue(notif.isSystemRemoved)
            assertNull(notif.imagePath)
            assertTrue(notif.history.isEmpty())
            assertNull(notif.contentIntentUri)
            assertNull(notif.progress)
            assertNull(notif.progressMax)
            assertNull(notif.isIndeterminate)
            assertTrue(extracted.ignoredApps.isEmpty())
        } finally {
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun writeBackupZipAndExtract_progressBarData_preservedInRoundTrip() {
        val notificationWithProgress = SerializableNotification(
            sbnKey = "0|com.download.mgr|101|null|1000",
            packageName = "com.download.mgr",
            title = "Downloading File",
            text = "45%",
            timestamp = 1700000050000L,
            appName = "Download Manager",
            isSystemRemoved = false,
            imagePath = null,
            history = emptyList(),
            contentIntentUri = null,
            progress = 45,
            progressMax = 100,
            isIndeterminate = false
        )

        val backupData = BackupData(
            notifications = listOf(notificationWithProgress),
            ignoredApps = emptyList()
        )

        val extractDir = Files.createTempDirectory("backup_progress_extract").toFile()
        try {
            val outStream = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, emptyList(), outStream)
            val extracted = BackupManager.extractBackupZip(ByteArrayInputStream(outStream.toByteArray()), extractDir)

            assertEquals(1, extracted.notifications.size)
            val notif = extracted.notifications[0]
            assertEquals(45, notif.progress)
            assertEquals(100, notif.progressMax)
            assertEquals(false, notif.isIndeterminate)
        } finally {
            extractDir.deleteRecursively()
        }
    }
}
