package com.fl0w.speye.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationHistoryEntity
import com.fl0w.speye.utils.SpeyeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class NotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var ignoredPackages = emptySet<String>()
    private lateinit var database: AppDatabase

    companion object {
        private const val MAX_BITMAP_DIMENSION = 1024
        private val REDACTION_SET = setOf(
            "hidden",
            "contents hidden",
            "content hidden",
            "sensitive content hidden",
            "sensitive notification content hidden",
            "redacted",
            // Turkish
            "i\u0307çerik gizlendi",
            "gizli",
            // German
            "inhalt verborgen",
            "verborgen",
            // Spanish
            "contenido oculto",
            "oculto",
            // Russian
            "содержимое скрыто",
            "скрыто",
            // French
            "contenu masqué",
            "masqué",
            // Chinese
            "内容已隐藏",
            // Korean
            "콘텐츠 숨김",
            // Japanese
            "コンテンツは非表示",
            // Arabic
            "المحتوى مخفي",
            // Portuguese
            "conteúdo oculto"
        )
    }

    private var currentImageSetting = com.fl0w.speye.data.settings.ImageFormatSetting.PNG

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)

        // #6: Cache ignored apps in-memory to avoid per-notification DB query
        serviceScope.launch {
            database.ignoredAppDao().getAllIgnoredApps().collectLatest { apps ->
                ignoredPackages = apps.map { it.packageName }.toSet()
                SpeyeLogger.d("NotificationService", "Ignored apps cache updated: ${ignoredPackages.size} apps")
            }
        }

        serviceScope.launch {
            com.fl0w.speye.data.settings.AppSettingsManager(this@NotificationService).imageFormat.collectLatest { format ->
                currentImageSetting = format
                SpeyeLogger.d("NotificationService", "Image format setting updated: $format")
            }
        }

        serviceScope.launch {
            com.fl0w.speye.utils.RetentionCleaner.pruneExpired(this@NotificationService)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // #9: Lifecycle logging
    override fun onListenerConnected() {
        super.onListenerConnected()
        SpeyeLogger.d("NotificationService", "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SpeyeLogger.d("NotificationService", "Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val sbnKey = sbn.key
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle()
        
        // #6: Fast in-memory check instead of DB query
        if (ignoredPackages.contains(packageName)) return

        // --- IMMEDIATE DATA EXTRACTION (Main Thread) ---
        // We must extract strings/images here because Android may clear the extras bundle 
        // once this method returns.
        
        // #10: Check visibility flag as primary redaction signal
        val isSecretVisibility = notification.visibility == Notification.VISIBILITY_SECRET

        var rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        
        // Handle MessagingStyle
        if (rawText == null || isRedacted(rawText, isSecretVisibility)) {
            val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            }
            if (messages != null && messages.isNotEmpty()) {
                val lastMessage = messages.last() as? Bundle
                val msgText = lastMessage?.getCharSequence("text")
                if (msgText != null) {
                    rawText = msgText
                }
            }
        }

        // Fallback for redaction
        if (isRedacted(rawText, isSecretVisibility)) {
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            rawText = when {
                !isRedacted(subText, isSecretVisibility) -> subText
                !isRedacted(summaryText, isSecretVisibility) -> summaryText
                else -> rawText
            }
        }

        // Fallback to ticker
        if (isRedacted(rawText, isSecretVisibility) && !isRedacted(notification.tickerText, isSecretVisibility)) {
            rawText = notification.tickerText
        }

        // #7: Prefer EXTRA_TITLE_BIG for BigTextStyle/BigPictureStyle notifications
        val finalTitle = extras.getString(Notification.EXTRA_TITLE_BIG) ?: extras.getString(Notification.EXTRA_TITLE)
        val finalTimestamp = sbn.postTime
        val finalIntentUri = extractIntentUri(notification.contentIntent)
        
        val finalHtmlText = (rawText?.let { 
            HtmlCompat.toHtml(android.text.SpannableString.valueOf(it), HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) 
        })?.toString()

        // #5 & #11: Comprehensive picture extraction (EXTRA_PICTURE, MessagingStyle URIs, Android 12+ pictureIcon, LargeIcon)
        @Suppress("DEPRECATION")
        val rawLargeIcon = extras.get(Notification.EXTRA_LARGE_ICON)
        val largeIconBitmap = when (rawLargeIcon) {
            is Bitmap -> scaleBitmapIfNeeded(rawLargeIcon)
            is Icon -> rawLargeIcon.loadDrawable(this)?.toBitmap()?.let { scaleBitmapIfNeeded(it) }
            else -> notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()?.let { scaleBitmapIfNeeded(it) }
        }

        val rawPicture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        }

        @Suppress("DEPRECATION")
        val pictureIcon = (extras.get("android.pictureIcon") as? Icon)?.loadDrawable(this)?.toBitmap()?.let { scaleBitmapIfNeeded(it) }
        val messagingStylePicture = extractMessagingStylePicture(extras)

        val picture = rawPicture?.let { scaleBitmapIfNeeded(it) } 
            ?: pictureIcon 
            ?: messagingStylePicture 
            ?: largeIconBitmap

        SpeyeLogger.d("NotificationService", "Intercepted (Immediate): $packageName - $finalTitle (Picture: ${picture != null})")

        // --- BACKGROUND PERSISTENCE ---
        serviceScope.launch {
            val appName = appNameCache.getOrPut(packageName) {
                try {
                    val appInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                    applicationContext.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
            }
            
            val imagePath = if (picture != null) saveBitmap(picture, sbnKey) else null

            val existing = database.notificationDao().getNotificationBySbnKey(sbnKey)
            if (existing != null) {
                val hasNewText = existing.text != finalHtmlText
                val hasNewImage = imagePath != null && imagePath != existing.imagePath

                if (hasNewText || hasNewImage) {
                    if (hasNewText && existing.text != null) {
                        database.notificationDao().insertHistory(
                            NotificationHistoryEntity(
                                notificationId = existing.id,
                                oldText = existing.text,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    // Delete old image file if replaced by a new image
                    if (hasNewImage && existing.imagePath != null) {
                        try {
                            File(existing.imagePath).delete()
                        } catch (e: Exception) {
                            SpeyeLogger.e("NotificationService", "Failed to delete old image", e)
                        }
                    }
                    database.notificationDao().update(
                        existing.copy(
                            text = finalHtmlText ?: existing.text,
                            timestamp = finalTimestamp,
                            isSystemRemoved = false,
                            imagePath = imagePath ?: existing.imagePath,
                            contentIntentUri = finalIntentUri ?: existing.contentIntentUri
                        )
                    )
                }
            } else {
                database.notificationDao().insert(
                    NotificationEntity(
                        sbnKey = sbnKey,
                        packageName = packageName,
                        appName = appName,
                        title = finalTitle,
                        text = finalHtmlText,
                        timestamp = finalTimestamp,
                        imagePath = imagePath,
                        contentIntentUri = finalIntentUri
                    )
                )
            }
        }
    }

    private fun extractIntentUri(pendingIntent: PendingIntent?): String? {
        if (pendingIntent == null) return null
        return try {
            val getIntentMethod = PendingIntent::class.java.getDeclaredMethod("getIntent")
            getIntentMethod.isAccessible = true
            val intent = getIntentMethod.invoke(pendingIntent) as? Intent
            intent?.toUri(Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            SpeyeLogger.e("NotificationService", "Failed to extract intent", e)
            null
        }
    }

    // #10: Accept visibility flag as primary redaction signal, string matching as fallback
    private fun isRedacted(text: CharSequence?, isSecretVisibility: Boolean = false): Boolean {
        if (text == null) return true
        if (isSecretVisibility) return true
        val s = text.toString().lowercase().trim()
        return REDACTION_SET.contains(s)
    }

    // #5: Scale down large bitmaps to prevent ANR on main/binder thread
    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= MAX_BITMAP_DIMENSION && h <= MAX_BITMAP_DIMENSION) return bitmap
        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    // Inspect MessagingStyle message bundles for attached image URIs (WhatsApp, Telegram, Signal, Messages)
    private fun extractMessagingStylePicture(extras: Bundle): Bitmap? {
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        } ?: return null
        for (i in messages.indices.reversed()) {
            val msg = messages[i] as? Bundle ?: continue
            val type = msg.getString("type")
            if (type != null && type.startsWith("image/")) {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    msg.getParcelable("uri", android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    msg.getParcelable<android.net.Uri>("uri")
                }
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                            if (bitmap != null) return scaleBitmapIfNeeded(bitmap)
                        }
                    } catch (e: Exception) {
                        SpeyeLogger.e("NotificationService", "Failed to load image from message URI: $uri", e)
                    }
                }
            }
        }
        return null
    }

    private fun saveBitmap(bitmap: Bitmap, key: String): String? {
        return try {
            val formatSetting = currentImageSetting
            val fileName = "img_${key.hashCode()}.${formatSetting.extension}"
            val file = File(filesDir, fileName)
            FileOutputStream(file).use { out ->
                val (compressFormat, quality) = when (formatSetting) {
                    com.fl0w.speye.data.settings.ImageFormatSetting.PNG -> Bitmap.CompressFormat.PNG to 100
                    com.fl0w.speye.data.settings.ImageFormatSetting.JPEG_HIGH -> Bitmap.CompressFormat.JPEG to 90
                    com.fl0w.speye.data.settings.ImageFormatSetting.JPEG_BALANCED -> Bitmap.CompressFormat.JPEG to 80
                    com.fl0w.speye.data.settings.ImageFormatSetting.WEBP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY to 75
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP to 75
                        }
                    }
                }
                bitmap.compress(compressFormat, quality, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            SpeyeLogger.e("NotificationService", "Error saving bitmap", e)
            null
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val sbnKey = sbn.key
        SpeyeLogger.d("NotificationService", "System removed: $sbnKey")
        serviceScope.launch {
            database.notificationDao().markAsSystemRemoved(sbnKey)
        }
    }
}
