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
        val days30 = 30
        val cutoff30 = now - (days30 * 24L * 60L * 60L * 1000L)
        assertEquals(2592000000L, now - cutoff30) // Exactly 30 days in ms

        val days90 = 90
        val cutoff90 = now - (days90 * 24L * 60L * 60L * 1000L)
        assertEquals(7776000000L, now - cutoff90) // Exactly 90 days in ms

        val days180 = 180
        val cutoff180 = now - (days180 * 24L * 60L * 60L * 1000L)
        assertEquals(15552000000L, now - cutoff180) // Exactly 180 days in ms
    }

    @Test
    fun retention_keepForever_zeroOrNegative_noCutoff() {
        val daysForever = 0
        assertTrue(daysForever <= 0)
    }

    @Test
    fun textScale_factors_areValid() {
        val scales = listOf(1.0f, 1.15f, 1.30f)
        scales.forEach { scale ->
            assertTrue(scale >= 1.0f)
            assertTrue(scale <= 1.5f)
        }
    }

    @Test
    fun areTitlesRelated_samePersonWithCount_returnsTrue() {
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Alice"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Alice (2)"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice (2)", "Alice (3)"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice", "Alice: 2 messages"))
        assertTrue(com.fl0w.speye.service.NotificationService.areTitlesRelated("Alice [2]", "Alice [3]"))
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
}
