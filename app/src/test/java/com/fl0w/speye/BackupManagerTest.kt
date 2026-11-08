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

    @Test
    fun notificationActionManager_encodesDecodesAndRoundTripsInBackup() {
        val titles = listOf("Confirm", "Reject")
        val encoded = com.fl0w.speye.utils.NotificationActionManager.encodeActionTitles(titles)
        assertEquals("Confirm\nReject", encoded)
        assertEquals(titles, com.fl0w.speye.utils.NotificationActionManager.decodeActionTitles(encoded))
        assertNull(com.fl0w.speye.utils.NotificationActionManager.encodeActionTitles(emptyList()))
        assertTrue(com.fl0w.speye.utils.NotificationActionManager.decodeActionTitles(null).isEmpty())

        val backup = BackupData(
            notifications = listOf(
                SerializableNotification(
                    sbnKey = "0|com.fl0w.speye|8891|null|10000",
                    packageName = "com.fl0w.speye",
                    title = "Speye Bank • Payment Request",
                    text = "Approve transfer of $240.00?",
                    timestamp = 1710009000000L,
                    appName = "Speye",
                    actionTitles = encoded
                )
            ),
            ignoredApps = emptyList()
        )
        val out = ByteArrayOutputStream()
        BackupManager.writeBackupZip(backup, emptyList(), out)
        val tempDir = createTempDir("speye_action_test")
        try {
            val extracted = BackupManager.extractBackupZip(ByteArrayInputStream(out.toByteArray()), tempDir)
            assertEquals(1, extracted.notifications.size)
            assertEquals("Confirm\nReject", extracted.notifications[0].actionTitles)
            assertEquals(
                listOf("Confirm", "Reject"),
                com.fl0w.speye.utils.NotificationActionManager.decodeActionTitles(extracted.notifications[0].actionTitles)
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun animatedWebpConverter_parsesChunksAndEncodesValidAnimatedGif() {
        // Construct a synthetic multi-frame Animated WebP container (RIFF....WEBP + VP8X + ANIM + 2 ANMF chunks)
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 0, 0)) // placeholder size
        out.write("WEBP".toByteArray(Charsets.US_ASCII))

        // VP8X chunk (size 10): flags=0x12 (alpha + animation), canvas 64x64 (stored as 63, 63 in 24-bit LE)
        out.write("VP8X".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(10, 0, 0, 0))
        out.write(byteArrayOf(0x12, 0, 0, 0, 63, 0, 0, 63, 0, 0))

        // ANIM chunk (size 6): bg=0, loopCount=0 (infinite)
        out.write("ANIM".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(6, 0, 0, 0))
        out.write(byteArrayOf(0, 0, 0, 0, 0, 0))

        // Two ANMF chunks with VP8L sub-chunk payload
        for (frameIdx in 0..1) {
            val vp8lPayload = byteArrayOf(
                'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'L'.code.toByte(),
                4, 0, 0, 0,
                0x2F, 0x00, 0x00, 0x00
            )
            val anmfSize = 16 + vp8lPayload.size
            out.write("ANMF".toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(anmfSize.toByte(), 0, 0, 0))
            // x=0, y=0, w-1=63, h-1=63, duration=100ms, flags=0x02 (doNotBlend)
            out.write(byteArrayOf(0, 0, 0, 0, 0, 0, 63, 0, 0, 63, 0, 0, 100, 0, 0, 0x02))
            out.write(vp8lPayload)
        }

        val webpBytes = out.toByteArray()
        val riffSize = webpBytes.size - 8
        webpBytes[4] = (riffSize and 0xFF).toByte()
        webpBytes[5] = ((riffSize ushr 8) and 0xFF).toByte()

        assertTrue(com.fl0w.speye.utils.AnimatedWebpConverter.isAnimatedWebp(webpBytes))
        val parsed = com.fl0w.speye.utils.AnimatedWebpConverter.parseAnimatedWebp(webpBytes)
        assertNotNull(parsed)
        assertEquals(64, parsed!!.canvasWidth)
        assertEquals(64, parsed.canvasHeight)
        assertEquals(0, parsed.loopCount)
        assertEquals(2, parsed.frames.size)
        assertEquals(100, parsed.frames[0].durationMs)
        // Verify standalone single-frame WebP wrapper starts with RIFF....WEBP
        assertEquals("RIFF", String(parsed.frames[0].singleFrameWebpBytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(parsed.frames[0].singleFrameWebpBytes, 8, 4, Charsets.US_ASCII))

        // Verify GIF89a encoding of 2 frames with transparency & colors
        val frame1Pixels = IntArray(4 * 4) { i -> if (i % 2 == 0) 0xFFFF0000.toInt() else 0x00000000 }
        val frame2Pixels = IntArray(4 * 4) { i -> if (i % 2 == 1) 0xFF00FF00.toInt() else 0x00000000 }
        val gifOut = ByteArrayOutputStream()
        com.fl0w.speye.utils.AnimatedWebpConverter.encodeGif(
            frames = listOf(
                com.fl0w.speye.utils.AnimatedWebpConverter.GifFrameData(4, 4, 100, frame1Pixels),
                com.fl0w.speye.utils.AnimatedWebpConverter.GifFrameData(4, 4, 120, frame2Pixels)
            ),
            loopCount = 0,
            out = gifOut
        )
        val gifBytes = gifOut.toByteArray()
        assertEquals("GIF89a", String(gifBytes, 0, 6, Charsets.US_ASCII))
        assertTrue(com.fl0w.speye.utils.ImageUtils.isAnimatedImage(gifBytes, "image/gif"))
        assertEquals(0x3B.toByte(), gifBytes.last()) // GIF trailer
        val gifAscii = String(gifBytes, Charsets.ISO_8859_1)
        assertTrue(gifAscii.contains("NETSCAPE2.0"))
    }

    @Test
    fun backupAndEncryptedRestore_animatedWebpAndGifStickers_preservedBitForBit() {
        val tempDir = Files.createTempDirectory("backup_sticker_test").toFile()
        val extractDir = Files.createTempDirectory("backup_sticker_extract").toFile()
        try {
            val animWebpBytes = byteArrayOf(
                0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x58,
                0x0A, 0x00, 0x00, 0x00, 0x12, 0x00, 0x00, 0x00,
                0x3F, 0x00, 0x00, 0x3F, 0x00, 0x00, 0x41, 0x4E,
                0x49, 0x4D, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
            )
            val animGifBytes = byteArrayOf(
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x10, 0x00,
                0x10, 0x00, 0xF0.toByte(), 0x00, 0x00, 0x21, 0xFF.toByte(), 0x0B,
                0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45,
                0x32, 0x2E, 0x30, 0x03, 0x01, 0x00, 0x00, 0x00,
                0x3B
            )

            val webpPath = com.fl0w.speye.utils.ImageUtils.saveRawImageFromBytes(animWebpBytes, "image/webp", tempDir)
            val gifPath = com.fl0w.speye.utils.ImageUtils.saveRawImageFromBytes(animGifBytes, "image/gif", tempDir)
            assertNotNull(webpPath)
            assertNotNull(gifPath)

            val webpFile = File(webpPath!!)
            val gifFile = File(gifPath!!)
            assertTrue(webpFile.name.endsWith(".webp"))
            assertTrue(gifFile.name.endsWith(".gif"))
            assertTrue(com.fl0w.speye.utils.ImageUtils.isAnimatedFile(webpFile.absolutePath))
            assertTrue(com.fl0w.speye.utils.ImageUtils.isAnimatedFile(gifFile.absolutePath))

            val n1 = SerializableNotification(
                sbnKey = "0|com.whatsapp|10|sticker1|1000",
                packageName = "com.whatsapp",
                title = "Alice",
                text = "Sticker",
                timestamp = 1700000100000L,
                appName = "WhatsApp",
                imagePath = webpFile.name
            )
            val n2 = SerializableNotification(
                sbnKey = "0|org.telegram.messenger|11|sticker2|1000",
                packageName = "org.telegram.messenger",
                title = "Bob",
                text = "GIF",
                timestamp = 1700000200000L,
                appName = "Telegram",
                imagePath = gifFile.name
            )

            val backupData = BackupData(notifications = listOf(n1, n2), ignoredApps = emptyList())
            val encryptedOut = ByteArrayOutputStream()
            BackupManager.encryptZipStream(
                rawStreamWriter = { cos ->
                    BackupManager.writeBackupZip(backupData, listOf(webpFile, gifFile), cos)
                },
                destination = encryptedOut,
                password = "StickerBackupPassword!"
            )

            val extracted = BackupManager.extractBackupZip(
                inputStream = ByteArrayInputStream(encryptedOut.toByteArray()),
                targetDir = extractDir,
                password = "StickerBackupPassword!"
            )

            assertEquals(2, extracted.notifications.size)
            val restoredWebp = File(extractDir, "attachments/${webpFile.name}")
            val restoredGif = File(extractDir, "attachments/${gifFile.name}")
            assertTrue(restoredWebp.exists())
            assertTrue(restoredGif.exists())
            assertArrayEquals(animWebpBytes, restoredWebp.readBytes())
            assertArrayEquals(animGifBytes, restoredGif.readBytes())
        } finally {
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun backupAndRestore_granularAppFilters_roundTripsCorrectly() {
        val allOn = com.fl0w.speye.data.model.IgnoredAppEntity.allOn("com.example.all_on")
        assertTrue(allOn.isAllOn)
        assertFalse(allOn.isAllOff)
        assertTrue(allOn.isMainToggleOn)

        val allOff = com.fl0w.speye.data.model.IgnoredAppEntity.allOff("com.example.all_off")
        assertFalse(allOff.isAllOn)
        assertTrue(allOff.isAllOff)
        assertFalse(allOff.isMainToggleOn)

        val partial = allOn.copy(packageName = "com.example.partial", allowLazyAssets = false, allowStickers = false)
        assertFalse(partial.isAllOn)
        assertFalse(partial.isAllOff)
        assertTrue(partial.isMainToggleOn)

        val backup = BackupData(
            notifications = emptyList(),
            ignoredApps = listOf("com.example.all_off"),
            appFilters = listOf(
                com.fl0w.speye.data.model.SerializableAppFilter(
                    packageName = "com.example.partial",
                    allowText = true,
                    allowLazyAssets = false,
                    allowStickers = false,
                    allowMedia = true,
                    allowVoice = true,
                    allowProgress = true,
                    allowActions = false
                ),
                com.fl0w.speye.data.model.SerializableAppFilter(
                    packageName = "com.example.all_off",
                    allowText = false,
                    allowLazyAssets = false,
                    allowStickers = false,
                    allowMedia = false,
                    allowVoice = false,
                    allowProgress = false,
                    allowActions = false
                )
            )
        )
        val out = ByteArrayOutputStream()
        BackupManager.writeBackupZip(backup, emptyList(), out)
        val tempDir = Files.createTempDirectory("speye_app_filter_test").toFile()
        try {
            val extracted = BackupManager.extractBackupZip(ByteArrayInputStream(out.toByteArray()), tempDir)
            assertEquals(1, extracted.ignoredApps.size)
            assertEquals("com.example.all_off", extracted.ignoredApps[0])
            assertEquals(2, extracted.appFilters.size)
            val p = extracted.appFilters.first { it.packageName == "com.example.partial" }
            assertTrue(p.allowText)
            assertFalse(p.allowLazyAssets)
            assertFalse(p.allowStickers)
            assertTrue(p.allowMedia)
            assertTrue(p.allowVoice)
            assertTrue(p.allowProgress)
            assertFalse(p.allowActions)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun encryptedBackup_multiChunkStream_roundTripsWithoutOom() {
        val tempDir = Files.createTempDirectory("speye_multichunk_src").toFile()
        val extractDir = Files.createTempDirectory("speye_multichunk_dst").toFile()
        try {
            // 220 KB random payload spanning 4 x 64 KB chunks
            val bigPayload = ByteArray(220 * 1024)
            java.util.Random(42L).nextBytes(bigPayload)
            val bigFile = File(tempDir, "big_attachment.bin").apply { writeBytes(bigPayload) }

            val notification = SerializableNotification(
                sbnKey = "0|com.example|99|null|1000",
                packageName = "com.example",
                title = "Large Backup Test",
                text = "Chunked AES-GCM stream test",
                timestamp = 1700000000000L,
                appName = "Example",
                isSystemRemoved = false,
                imagePath = bigFile.name
            )
            val backupData = BackupData(notifications = listOf(notification), ignoredApps = emptyList())
            val encryptedFile = File(tempDir, "encrypted_multi.spy")

            encryptedFile.outputStream().use { fos ->
                BackupManager.encryptZipStream(
                    rawStreamWriter = { cos ->
                        BackupManager.writeBackupZip(backupData, listOf(bigFile), cos)
                    },
                    destination = fos,
                    password = "MultiChunkPassword#2026"
                )
            }

            val header = encryptedFile.readBytes().copyOfRange(0, 9).toString(Charsets.UTF_8)
            assertEquals("SPYE_ENC2", header)

            val extracted = encryptedFile.inputStream().use { fis ->
                BackupManager.extractBackupZip(fis, extractDir, "MultiChunkPassword#2026")
            }
            assertEquals(1, extracted.notifications.size)
            val restoredAttachment = File(extractDir, "attachments/${bigFile.name}")
            assertTrue(restoredAttachment.exists())
            assertArrayEquals(bigPayload, restoredAttachment.readBytes())
        } finally {
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun animatedGifEncoder_adaptivePalette_preservesRichGradientColors() {
        val width = 32
        val height = 32
        val pixels = IntArray(width * height)
        val inputRgbSet = HashSet<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 7 + 20).coerceIn(0, 255)
                val g = (y * 7 + 15).coerceIn(0, 255)
                val b = ((x + y) * 3 + 40).coerceIn(0, 255)
                val rgb = (r shl 16) or (g shl 8) or b
                inputRgbSet.add(rgb)
                pixels[y * width + x] = (0xFF shl 24) or rgb
            }
        }
        val frame = com.fl0w.speye.utils.AnimatedWebpConverter.GifFrameData(
            width = width,
            height = height,
            delayMs = 50,
            argbPixels = pixels
        )
        val out = ByteArrayOutputStream()
        com.fl0w.speye.utils.AnimatedWebpConverter.encodeGif(listOf(frame), loopCount = 0, out = out)
        val gifBytes = out.toByteArray()

        // Global Color Table starts at byte 13 and is 768 bytes (256 * 3)
        val paletteColors = HashSet<Int>()
        for (i in 0 until 255) {
            val base = 13 + i * 3
            val r = gifBytes[base].toInt() and 0xFF
            val g = gifBytes[base + 1].toInt() and 0xFF
            val b = gifBytes[base + 2].toInt() and 0xFF
            val rgb = (r shl 16) or (g shl 8) or b
            if (rgb != 0) paletteColors.add(rgb)
        }
        // Old 6x7x6 static cube only matched ~18 colors in this narrow gradient range;
        // adaptive Median-Cut palette populates >180 distinct gradient shades.
        assertTrue("Expected >180 distinct adaptive palette colors, got ${paletteColors.size}", paletteColors.size > 180)
    }
}
