package com.fl0w.speye

import com.fl0w.speye.data.settings.ImageFormatSetting
import org.junit.Assert.*
import org.junit.Test

class AppSettingsAndRetentionTest {

    @Test
    fun imageFormatSetting_fromString_fallbackAndParsing() {
        assertEquals(ImageFormatSetting.PNG, ImageFormatSetting.fromString("PNG"))
        assertEquals(ImageFormatSetting.JPEG_HIGH, ImageFormatSetting.fromString("JPEG_HIGH"))
        assertEquals(ImageFormatSetting.JPEG_BALANCED, ImageFormatSetting.fromString("JPEG_BALANCED"))
        assertEquals(ImageFormatSetting.WEBP, ImageFormatSetting.fromString("WEBP"))
        assertEquals(ImageFormatSetting.PNG, ImageFormatSetting.fromString("INVALID_FORMAT"))
    }

    @Test
    fun retentionCutoff_calculation_isAccurate() {
        val now = 1700000000000L
        val cutoff30 = com.fl0w.speye.utils.RetentionCleaner.calculateCutoffTimestamp(30, now)
        assertNotNull(cutoff30)
        assertEquals(2592000000L, now - cutoff30!!)

        val cutoff90 = com.fl0w.speye.utils.RetentionCleaner.calculateCutoffTimestamp(90, now)
        assertNotNull(cutoff90)
        assertEquals(7776000000L, now - cutoff90!!)
    }

    @Test
    fun retention_keepForever_zeroOrNegative_returnsNullCutoff() {
        val now = 1700000000000L
        assertNull(com.fl0w.speye.utils.RetentionCleaner.calculateCutoffTimestamp(0, now))
        assertNull(com.fl0w.speye.utils.RetentionCleaner.calculateCutoffTimestamp(-1, now))
    }

    @Test
    fun areTitlesRelated_samePersonWithCount_returnsTrue() {
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Alice"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Alice (2)"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice (2)", "Alice (3)"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Alice: 2 messages"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice [2]", "Alice [3]"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Test [A]", "Test [B]"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Test [A]", "Test"))
    }

    @Test
    fun areTitlesRelated_emailPrefixEdgeCases_correctlyDifferentiates() {
        // Same topic with/without Re: / Fwd: prefix should relate
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Re: Project Alpha", "Project Alpha"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Fwd: Project Alpha", "Project Alpha"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Re: Project Alpha", "Re: Project Alpha (2)"))

        // Different topics sharing Re: prefix must NOT relate
        assertFalse(com.fl0w.speye.service.NotificationService.areTitlesRelated("Re: Project Alpha", "Re: Budget Plan"))
        assertFalse(com.fl0w.speye.service.NotificationService.areTitlesRelated("Fwd: Lunch", "Fwd: Dinner"))
    }

    @Test
    fun areTitlesRelated_differentSenders_returnsFalse() {
        assertFalse(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Bob"))
        assertFalse(com.fl0w.speye.service.NotificationService.areTitlesRelated("Person A", "Person B"))
        assertFalse(com.fl0w.speye.service.NotificationService.areTitlesRelated("John Smith", "Jane Doe"))
    }

    @Test
    fun areTitlesRelated_nullHandling_returnsTrue() {
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated(null, "Alice"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", null))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated(null, null))
    }

    @Test
    fun resolveOrSaveImageFile_deduplicationAndFormatReuse() {
        val tempDir = java.nio.file.Files.createTempDirectory("speye_test_dedupe").toFile()
        try {
            var writeCount = 0
            val hash = "deadbeef1234"

            // First save writes file
            val path1 = com.fl0w.speye.utils.ImageUtils.resolveOrSaveImageFile(
                directory = tempDir,
                hash = hash,
                formatSetting = ImageFormatSetting.PNG
            ) { file ->
                writeCount++
                file.writeText("fake image bytes")
            }
            assertNotNull(path1)
            assertEquals(1, writeCount)
            assertTrue(java.io.File(path1!!).exists())

            // Second save with same hash and format: fast-path skips writeBytes
            val path2 = com.fl0w.speye.utils.ImageUtils.resolveOrSaveImageFile(
                directory = tempDir,
                hash = hash,
                formatSetting = ImageFormatSetting.PNG
            ) { file ->
                writeCount++
                file.writeText("fake image bytes")
            }
            assertEquals(path1, path2)
            assertEquals(1, writeCount) // writeCount did NOT increase

            // Third save with different format setting: reuses existing alternative without writing
            val path3 = com.fl0w.speye.utils.ImageUtils.resolveOrSaveImageFile(
                directory = tempDir,
                hash = hash,
                formatSetting = ImageFormatSetting.WEBP
            ) { file ->
                writeCount++
                file.writeText("fake image bytes")
            }
            assertEquals(path1, path3) // Reuses existing PNG file
            assertEquals(1, writeCount) // writeCount still did NOT increase
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun imageUtils_computeHash_isDeterministic() {
        val sampleBytes = ByteArray(64) { it.toByte() }
        val hash1 = com.fl0w.speye.utils.ImageUtils.computeHash(sampleBytes, 8, 8, "ARGB_8888")
        val hash2 = com.fl0w.speye.utils.ImageUtils.computeHash(sampleBytes, 8, 8, "ARGB_8888")
        assertEquals(hash1, hash2)
        assertEquals(32, hash1.length) // Standard 128-bit MD5 hex length
    }

    @Test
    fun imageUtils_computeHash_differentContent_producesDifferentHash() {
        val bytes1 = ByteArray(64) { it.toByte() }
        val bytes2 = ByteArray(64) { (it + 1).toByte() }
        val hash1 = com.fl0w.speye.utils.ImageUtils.computeHash(bytes1, 8, 8, "ARGB_8888")
        val hash2 = com.fl0w.speye.utils.ImageUtils.computeHash(bytes2, 8, 8, "ARGB_8888")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun imageUtils_computeHash_differentDimensions_producesDifferentHash() {
        val bytes = ByteArray(64) { it.toByte() }
        val hash1 = com.fl0w.speye.utils.ImageUtils.computeHash(bytes, 8, 8, "ARGB_8888")
        val hash2 = com.fl0w.speye.utils.ImageUtils.computeHash(bytes, 16, 4, "ARGB_8888")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun imageUtils_computeHash_differentConfig_producesDifferentHash() {
        val bytes = ByteArray(64) { it.toByte() }
        val hash1 = com.fl0w.speye.utils.ImageUtils.computeHash(bytes, 8, 8, "ARGB_8888")
        val hash2 = com.fl0w.speye.utils.ImageUtils.computeHash(bytes, 8, 8, "RGB_565")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun sweepOrphanFiles_recentFileWithinGracePeriod_isNotDeleted() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("img_recent", ".webp")
        try {
            val now = 1_000_000_000L
            tempFile.setLastModified(now - 60_000L) // 1 minute old
            val gracePeriod = 600_000L // 10 minutes

            val deleted = com.fl0w.speye.utils.RetentionCleaner.sweepOrphanFiles(
                files = listOf(tempFile),
                now = now,
                gracePeriodMs = gracePeriod,
                isReferenced = { false }
            )

            assertEquals(0, deleted)
            assertTrue(tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun sweepOrphanFiles_oldUnreferencedFile_isDeleted() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("img_old_orphan", ".webp")
        val now = 1_000_000_000L
        tempFile.setLastModified(now - 700_000L) // 11.6 minutes old
        val gracePeriod = 600_000L // 10 minutes

        val deleted = com.fl0w.speye.utils.RetentionCleaner.sweepOrphanFiles(
            files = listOf(tempFile),
            now = now,
            gracePeriodMs = gracePeriod,
            isReferenced = { false }
        )

        assertEquals(1, deleted)
        assertFalse(tempFile.exists())
    }

    @Test
    fun sweepOrphanFiles_oldReferencedFile_isNotDeleted() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("img_old_referenced", ".webp")
        try {
            val now = 1_000_000_000L
            tempFile.setLastModified(now - 700_000L)
            val gracePeriod = 600_000L

            val deleted = com.fl0w.speye.utils.RetentionCleaner.sweepOrphanFiles(
                files = listOf(tempFile),
                now = now,
                gracePeriodMs = gracePeriod,
                isReferenced = { true }
            )

            assertEquals(0, deleted)
            assertTrue(tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }
}


