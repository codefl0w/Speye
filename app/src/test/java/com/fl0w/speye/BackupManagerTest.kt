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

    @Test
    fun writeBackupZipAndExtract_mediaAndVoiceMessage_preservedInRoundTrip() {
        val tempDir = Files.createTempDirectory("backup_media_voice_test").toFile()
        val extractDir = Files.createTempDirectory("backup_media_voice_extract").toFile()
        try {
            val audioFile = File(tempDir, "aud_test123.ogg")
            audioFile.writeBytes("dummy ogg audio data".toByteArray(Charsets.UTF_8))

            val mediaNotification = SerializableNotification(
                sbnKey = "0|com.spotify.music|1|tag|1000",
                packageName = "com.spotify.music",
                title = "Song Title",
                text = "Artist Name",
                timestamp = 1700000010000L,
                appName = "Spotify",
                isSystemRemoved = false,
                imagePath = null,
                history = emptyList(),
                isMedia = true,
                mediaTitle = "Song Title",
                mediaArtist = "Artist Name",
                mediaAlbum = "Greatest Hits",
                mediaDurationMs = 210000L,
                mediaPositionMs = 45000L,
                mediaPlaybackState = 3
            )

            val voiceNotification = SerializableNotification(
                sbnKey = "0|com.whatsapp|2|voice|1000",
                packageName = "com.whatsapp",
                title = null,
                text = "0:17",
                timestamp = 1700000020000L,
                appName = "WhatsApp",
                isSystemRemoved = false,
                imagePath = null,
                history = emptyList(),
                audioPath = "aud_test123.ogg",
                isVoiceMessage = true,
                voiceSender = "Bob"
            )

            val backupData = BackupData(
                notifications = listOf(mediaNotification, voiceNotification),
                ignoredApps = listOf("com.ignore.app")
            )

            val outStream = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, listOf(audioFile), outStream)
            val extracted = BackupManager.extractBackupZip(ByteArrayInputStream(outStream.toByteArray()), extractDir)

            assertEquals(2, extracted.notifications.size)
            val extractedMedia = extracted.notifications.first { it.isMedia }
            assertTrue(extractedMedia.isMedia)
            assertEquals("Song Title", extractedMedia.mediaTitle)
            assertEquals("Artist Name", extractedMedia.mediaArtist)
            assertEquals("Greatest Hits", extractedMedia.mediaAlbum)
            assertEquals(210000L, extractedMedia.mediaDurationMs)
            assertEquals(45000L, extractedMedia.mediaPositionMs)
            assertEquals(3, extractedMedia.mediaPlaybackState)

            val extractedVoice = extracted.notifications.first { it.isVoiceMessage }
            assertTrue(extractedVoice.isVoiceMessage)
            assertEquals("Bob", extractedVoice.voiceSender)
            assertEquals("aud_test123.ogg", extractedVoice.audioPath)

            val extractedAudioAttachment = File(extractDir, "attachments/aud_test123.ogg")
            assertTrue(extractedAudioAttachment.exists())
            assertEquals("dummy ogg audio data", extractedAudioAttachment.readText(Charsets.UTF_8))
        } finally {
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun encryptedBackup_correctPassword_decryptsSuccessfully() {
        val extractDir = Files.createTempDirectory("backup_enc_test").toFile()
        try {
            val backupData = BackupData(
                notifications = listOf(
                    SerializableNotification(
                        sbnKey = "0|com.enc.test|1|null|1000",
                        packageName = "com.enc.test",
                        title = "Secret",
                        text = "Confidential notification",
                        timestamp = 1700000000000L,
                        appName = "EncApp",
                        isSystemRemoved = false,
                        imagePath = null,
                        history = emptyList()
                    )
                ),
                ignoredApps = emptyList()
            )

            val rawBaos = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, emptyList(), rawBaos)
            val encryptedBytes = BackupManager.encryptZipBytes(rawBaos.toByteArray(), "MySecretPassword123")

            val extracted = BackupManager.extractBackupZip(
                ByteArrayInputStream(encryptedBytes),
                extractDir,
                password = "MySecretPassword123"
            )

            assertEquals(1, extracted.notifications.size)
            assertEquals("Secret", extracted.notifications[0].title)
            assertEquals("Confidential notification", extracted.notifications[0].text)
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun encryptedBackup_missingPassword_throwsEncryptedBackupException() {
        val extractDir = Files.createTempDirectory("backup_enc_missing").toFile()
        try {
            val backupData = BackupData(notifications = emptyList(), ignoredApps = emptyList())
            val rawBaos = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, emptyList(), rawBaos)
            val encryptedBytes = BackupManager.encryptZipBytes(rawBaos.toByteArray(), "MySecretPassword123")

            try {
                BackupManager.extractBackupZip(
                    ByteArrayInputStream(encryptedBytes),
                    extractDir,
                    password = null
                )
                fail("Expected EncryptedBackupException when password is null")
            } catch (e: com.fl0w.speye.utils.EncryptedBackupException) {
                assertTrue(e.needsPassword)
            }
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun encryptedBackup_wrongPassword_throwsEncryptedBackupException() {
        val extractDir = Files.createTempDirectory("backup_enc_wrong").toFile()
        try {
            val backupData = BackupData(notifications = emptyList(), ignoredApps = emptyList())
            val rawBaos = ByteArrayOutputStream()
            BackupManager.writeBackupZip(backupData, emptyList(), rawBaos)
            val encryptedBytes = BackupManager.encryptZipBytes(rawBaos.toByteArray(), "MySecretPassword123")

            try {
                BackupManager.extractBackupZip(
                    ByteArrayInputStream(encryptedBytes),
                    extractDir,
                    password = "WrongPassword!"
                )
                fail("Expected EncryptedBackupException when password is wrong")
            } catch (e: com.fl0w.speye.utils.EncryptedBackupException) {
                assertTrue(e.isInvalidPassword)
            }
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun deriveKeyFallback_producesIdenticalBytesToJcePbkdf2() {
        val password = "TestPassword_123!@#"
        val salt = "1234567890123456".toByteArray(Charsets.UTF_8)
        val jceKey = BackupManager.deriveKey(password, salt)
        val fallbackKey = BackupManager.deriveKeyFallback(password, salt)
        assertArrayEquals(jceKey, fallbackKey)
    }

    @Test
    fun encryptedBackup_streamingRoundTrip_success() {
        val tempDir = Files.createTempDirectory("backup_stream_test").toFile()
        val extractDir = Files.createTempDirectory("backup_stream_extract").toFile()
        try {
            val attachmentFile = File(tempDir, "aud_test.ogg")
            attachmentFile.writeBytes("voice note streaming bytes".toByteArray(Charsets.UTF_8))

            val notification = SerializableNotification(
                sbnKey = "0|com.example.voice|1|tag|2000",
                packageName = "com.example.voice",
                title = "Bob",
                text = "Voice message",
                timestamp = 1700000000456L,
                appName = "VoiceApp",
                audioPath = "aud_test.ogg",
                isVoiceMessage = true
            )
            val backupData = BackupData(notifications = listOf(notification), ignoredApps = emptyList())

            val encryptedOut = ByteArrayOutputStream()
            BackupManager.encryptZipStream(
                rawStreamWriter = { cos ->
                    BackupManager.writeBackupZip(backupData, listOf(attachmentFile), cos)
                },
                destination = encryptedOut,
                password = "StreamPassword456"
            )
            val encryptedBytes = encryptedOut.toByteArray()
            assertTrue(encryptedBytes.isNotEmpty())

            val extracted = BackupManager.extractBackupZip(
                inputStream = ByteArrayInputStream(encryptedBytes),
                targetDir = extractDir,
                password = "StreamPassword456"
            )

            assertEquals(1, extracted.notifications.size)
            assertEquals("Bob", extracted.notifications[0].title)
            assertEquals("aud_test.ogg", extracted.notifications[0].audioPath)
            assertTrue(extracted.notifications[0].isVoiceMessage)

            val extractedAudio = File(extractDir, "attachments/aud_test.ogg")
            assertTrue(extractedAudio.exists())
            assertEquals("voice note streaming bytes", extractedAudio.readText(Charsets.UTF_8))
        } finally {
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun imageUtils_isAnimatedImage_detectsGifAndAnimatedWebP() {
        val gifBytes = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00)
        assertTrue(com.fl0w.speye.utils.ImageUtils.isAnimatedImage(gifBytes))

        // WebP with ANIM chunk
        val webpAnimHeader = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x20, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x41, 0x4E, 0x49, 0x4D,
            0x00, 0x00, 0x00, 0x00
        )
        assertTrue(com.fl0w.speye.utils.ImageUtils.isAnimatedImage(webpAnimHeader))

        // Static JPEG
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01)
        assertFalse(com.fl0w.speye.utils.ImageUtils.isAnimatedImage(jpegBytes, "image/jpeg"))

        // Static PNG
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)
        assertFalse(com.fl0w.speye.utils.ImageUtils.isAnimatedImage(pngBytes, "image/png"))
    }
}
