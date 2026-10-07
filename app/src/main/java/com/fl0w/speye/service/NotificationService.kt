package com.fl0w.speye.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationHistoryEntity
import com.fl0w.speye.utils.ImageUtils
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

        fun areTitlesRelated(t1: String?, t2: String?): Boolean {
            if (t1 == null || t2 == null) return true
            val s1 = t1.trim().lowercase()
            val s2 = t2.trim().lowercase()
            if (s1 == s2) return true

            // Strip standard email subject prefixes (re:, fwd:, fw:)
            val prefixRegex = Regex("""^(?:re|fwd|fw)\s*:\s*""", RegexOption.IGNORE_CASE)
            val clean1 = s1.replace(prefixRegex, "").trim()
            val clean2 = s2.replace(prefixRegex, "").trim()
            if (clean1 == clean2) return true

            // Strip trailing message count or counters: "Alice (2)", "Alice [3]", "Alice: 2 messages"
            val suffixRegex = Regex("""\s*[\(\[].*?$|\s*:\s*\d+\s*(?:new\s*)?messages?.*$""")
            val base1 = clean1.replace(suffixRegex, "").trim()
            val base2 = clean2.replace(suffixRegex, "").trim()
            if (base1.isNotEmpty() && base1 == base2) return true

            if (clean1.isNotEmpty() && clean2.isNotEmpty()) {
                if (clean1.startsWith("$clean2 ") || clean2.startsWith("$clean1 ")) return true
            }
            return false
        }

        private const val PROGRESS_THROTTLE_MS = 250L
        private val lastProgressUpdateTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val lastProgressValues = java.util.concurrent.ConcurrentHashMap<String, Int>()

        private const val MEDIA_THROTTLE_MS = 500L
        private val lastMediaUpdateTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val lastMediaStates = java.util.concurrent.ConcurrentHashMap<String, Int>()
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

        // Skip group summary container notifications (child notifications hold actual conversation data)
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            SpeyeLogger.d("NotificationService", "Ignored group summary: $packageName ($sbnKey)")
            return
        }

        // --- IMMEDIATE DATA EXTRACTION (Main Thread) ---
        // We must extract strings/images here because Android may clear the extras bundle 
        // once this method returns.
        
        // #10: Check visibility flag as primary redaction signal
        val isSecretVisibility = notification.visibility == Notification.VISIBILITY_SECRET

        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val lastMessage = messages?.lastOrNull() as? Bundle

        var rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        
        // Handle MessagingStyle
        if (rawText == null || isRedacted(rawText, isSecretVisibility)) {
            val msgText = lastMessage?.getCharSequence("text")
            if (msgText != null) {
                rawText = msgText
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

        // Robust title extraction: CharSequence support + MessagingStyle conversation/sender fallback
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)

        val senderFromMessage = lastMessage?.getCharSequence("sender")?.toString()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                (lastMessage?.getParcelable("sender_person") as? android.app.Person)?.name?.toString()
            } else null

        val finalTitle = (rawTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: senderFromMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() })

        val finalTimestamp = sbn.postTime
        val finalIntentUri = extractIntentUri(notification.contentIntent)
        
        val finalHtmlText = (rawText?.let { 
            HtmlCompat.toHtml(android.text.SpannableString.valueOf(it), HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) 
        })?.toString()

        val rawProgressMax = if (extras.containsKey(Notification.EXTRA_PROGRESS_MAX)) {
            extras.getInt(Notification.EXTRA_PROGRESS_MAX)
        } else null

        val rawProgress = if (extras.containsKey(Notification.EXTRA_PROGRESS)) {
            extras.getInt(Notification.EXTRA_PROGRESS)
        } else null

        val rawIsIndeterminate = if (extras.containsKey(Notification.EXTRA_PROGRESS_INDETERMINATE)) {
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        } else null

        val hasActiveProgressBar = (rawProgressMax != null && rawProgressMax > 0) || (rawIsIndeterminate == true)
        val finalProgress = if (hasActiveProgressBar) rawProgress else null
        val finalProgressMax = if (hasActiveProgressBar) rawProgressMax else null
        val finalIsIndeterminate = if (hasActiveProgressBar) rawIsIndeterminate else null

        // Voice message detection
        var detectedVoiceUri: Uri? = null
        var detectedVoiceMime: String? = null
        var detectedVoiceSender: String? = senderFromMessage

        if (messages != null) {
            for (msgObj in messages) {
                val msg = msgObj as? Bundle ?: continue
                val type = msg.getString("type")
                if (type?.startsWith("audio/", ignoreCase = true) == true) {
                    detectedVoiceMime = type
                    val uriObj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        msg.getParcelable("uri", Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        msg.getParcelable("uri")
                    } ?: (msg.get("uri") as? Uri)
                    if (uriObj != null) {
                        detectedVoiceUri = uriObj
                    }
                    val msgSender = msg.getCharSequence("sender")?.toString()
                        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            @Suppress("DEPRECATION")
                            (msg.getParcelable("sender_person") as? android.app.Person)?.name?.toString()
                        } else null
                    if (!msgSender.isNullOrBlank()) {
                        detectedVoiceSender = msgSender
                    }
                }
            }
        }

        if (detectedVoiceUri == null) {
            val audioContentUri = if (extras.containsKey("android.audioContentsURI")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable("android.audioContentsURI", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable("android.audioContentsURI")
                } ?: (extras.get("android.audioContentsURI") as? Uri)
                ?: extras.getString("android.audioContentsURI")?.let { Uri.parse(it) }
            } else null
            if (audioContentUri != null) {
                detectedVoiceUri = audioContentUri
                detectedVoiceMime = "audio/ogg"
            }
        }

        val isTestVoice = extras.getBoolean("speye_test_voice_message", false)
        val testAudioPath = extras.getString("speye_test_audio_path")
        val isVoiceMessageDetected = detectedVoiceUri != null || isTestVoice || testAudioPath != null

        // Media detection and extraction
        val mediaSnapshot = com.fl0w.speye.utils.MediaSessionExtractor.extract(this, sbn)

        // #5 & #11: Lazy picture extraction (avoids simultaneous decode of multiple bitmap sources)
        val picture = mediaSnapshot.coverBitmap ?: extractNotificationPicture(notification, extras)

        SpeyeLogger.d("NotificationService", "Intercepted (Immediate): $packageName - $finalTitle (Picture: ${picture != null}, Media: ${mediaSnapshot.isMedia}, Voice: $isVoiceMessageDetected, Progress: $finalProgress/$finalProgressMax, Indet: $finalIsIndeterminate)")

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
            
            val imagePath = if (picture != null) ImageUtils.saveBitmap(this@NotificationService, picture, currentImageSetting) else null

            val isOurApp = packageName == applicationContext.packageName
            val existing = database.notificationDao().getActiveNotificationBySbnKey(sbnKey)

            if (isVoiceMessageDetected) {
                val audioSaved = if (detectedVoiceUri != null) {
                    com.fl0w.speye.utils.AudioUtils.saveAudioFromUri(this@NotificationService, detectedVoiceUri, detectedVoiceMime)
                } else null
                val effectiveAudioPath = audioSaved?.first ?: testAudioPath ?: existing?.audioPath
                val extractedDuration = audioSaved?.second?.takeIf { it > 0L }
                    ?: (effectiveAudioPath?.let { com.fl0w.speye.utils.AudioUtils.extractDuration(it) })?.takeIf { it > 0L }
                    ?: com.fl0w.speye.utils.AudioUtils.parseDurationFromText(rawText?.toString())
                    ?: com.fl0w.speye.utils.AudioUtils.parseDurationFromText(finalTitle)
                    ?: existing?.mediaDurationMs
                    ?: 0L
                val effectiveVoiceSender = detectedVoiceSender ?: finalTitle ?: existing?.voiceSender ?: "Voice message"

                val isSameVoiceMessage = existing != null && existing.isVoiceMessage && existing.packageName == packageName

                if (isSameVoiceMessage) {
                    database.notificationDao().update(
                        existing.copy(
                            title = finalTitle ?: existing.title,
                            text = finalHtmlText ?: existing.text,
                            timestamp = finalTimestamp,
                            isSystemRemoved = false,
                            imagePath = imagePath ?: existing.imagePath,
                            contentIntentUri = finalIntentUri ?: existing.contentIntentUri,
                            audioPath = effectiveAudioPath ?: existing.audioPath,
                            isVoiceMessage = true,
                            voiceSender = effectiveVoiceSender,
                            mediaDurationMs = if (extractedDuration > 0L) extractedDuration else existing.mediaDurationMs,
                            mediaPositionMs = finalProgress?.toLong() ?: existing.mediaPositionMs ?: 0L
                        )
                    )
                    return@launch
                } else {
                    if (existing != null) {
                        database.notificationDao().markAsSystemRemovedById(existing.id)
                    }
                    database.notificationDao().insert(
                        NotificationEntity(
                            sbnKey = sbnKey,
                            packageName = packageName,
                            appName = appName,
                            title = finalTitle,
                            text = finalHtmlText,
                            timestamp = finalTimestamp,
                            imagePath = imagePath,
                            contentIntentUri = finalIntentUri,
                            audioPath = effectiveAudioPath,
                            isVoiceMessage = true,
                            voiceSender = effectiveVoiceSender,
                            mediaDurationMs = extractedDuration,
                            mediaPositionMs = finalProgress?.toLong() ?: 0L,
                            mediaPlaybackState = android.media.session.PlaybackState.STATE_PAUSED
                        )
                    )
                    return@launch
                }
            }

            if (mediaSnapshot.isMedia) {
                val effectiveMediaTitle = mediaSnapshot.title ?: finalTitle
                val effectiveMediaArtist = mediaSnapshot.artist ?: finalHtmlText
                val isSameTrack = existing != null &&
                        existing.isMedia &&
                        existing.packageName == packageName &&
                        areTitlesRelated(existing.mediaTitle ?: existing.title, effectiveMediaTitle) &&
                        (existing.mediaArtist == null || effectiveMediaArtist == null || existing.mediaArtist == effectiveMediaArtist)

                if (isSameTrack) {
                    val now = System.currentTimeMillis()
                    val lastUpdate = lastMediaUpdateTimestamps[sbnKey] ?: 0L
                    val lastState = lastMediaStates[sbnKey]
                    val stateChanged = mediaSnapshot.playbackState != null && mediaSnapshot.playbackState != lastState

                    if (!stateChanged && (now - lastUpdate < MEDIA_THROTTLE_MS)) {
                        return@launch
                    }

                    database.notificationDao().update(
                        existing.copy(
                            title = effectiveMediaTitle ?: existing.title,
                            text = effectiveMediaArtist ?: existing.text,
                            timestamp = finalTimestamp,
                            isSystemRemoved = false,
                            imagePath = imagePath ?: existing.imagePath,
                            contentIntentUri = finalIntentUri ?: existing.contentIntentUri,
                            isMedia = true,
                            mediaTitle = mediaSnapshot.title ?: existing.mediaTitle,
                            mediaArtist = mediaSnapshot.artist ?: existing.mediaArtist,
                            mediaAlbum = mediaSnapshot.album ?: existing.mediaAlbum,
                            mediaDurationMs = mediaSnapshot.durationMs ?: existing.mediaDurationMs,
                            mediaPositionMs = mediaSnapshot.positionMs ?: existing.mediaPositionMs,
                            mediaPlaybackState = mediaSnapshot.playbackState ?: existing.mediaPlaybackState
                        )
                    )
                    lastMediaUpdateTimestamps[sbnKey] = now
                    mediaSnapshot.playbackState?.let { lastMediaStates[sbnKey] = it }
                    return@launch
                } else {
                    // New track started on same sbnKey: mark previous track as system removed and paused
                    if (existing != null) {
                        database.notificationDao().update(
                            existing.copy(
                                isSystemRemoved = true,
                                mediaPlaybackState = android.media.session.PlaybackState.STATE_PAUSED
                            )
                        )
                    }
                    database.notificationDao().insert(
                        NotificationEntity(
                            sbnKey = sbnKey,
                            packageName = packageName,
                            appName = appName,
                            title = effectiveMediaTitle,
                            text = effectiveMediaArtist,
                            timestamp = finalTimestamp,
                            imagePath = imagePath,
                            contentIntentUri = finalIntentUri,
                            isMedia = true,
                            mediaTitle = mediaSnapshot.title ?: finalTitle,
                            mediaArtist = mediaSnapshot.artist,
                            mediaAlbum = mediaSnapshot.album,
                            mediaDurationMs = mediaSnapshot.durationMs,
                            mediaPositionMs = mediaSnapshot.positionMs,
                            mediaPlaybackState = mediaSnapshot.playbackState
                        )
                    )
                    lastMediaUpdateTimestamps[sbnKey] = System.currentTimeMillis()
                    mediaSnapshot.playbackState?.let { lastMediaStates[sbnKey] = it }
                    return@launch
                }
            }

            val titlesMatch = isOurApp || areTitlesRelated(existing?.title, finalTitle)
            val isExistingProgress = (existing?.progressMax != null && existing.progressMax > 0) || existing?.isIndeterminate == true
            val isNewProgressCycle = existing != null && existing.progress != null && existing.progressMax != null &&
                    existing.progress >= existing.progressMax &&
                    finalProgress != null && finalProgress < existing.progress

            val isSameNotificationContext = existing != null && titlesMatch && !isNewProgressCycle
            val isProgressUpdate = (hasActiveProgressBar || isExistingProgress) && isSameNotificationContext

            if (isProgressUpdate && hasActiveProgressBar) {
                val now = System.currentTimeMillis()
                val lastUpdate = lastProgressUpdateTimestamps[sbnKey] ?: 0L
                val lastVal = lastProgressValues[sbnKey]

                val isTerminalMilestone = finalProgress != null && finalProgressMax != null && finalProgressMax > 0 &&
                        (finalProgress == 0 || finalProgress >= finalProgressMax)
                val hasSignificantProgressDelta = finalProgress != null && finalProgressMax != null && finalProgressMax > 0 &&
                        lastVal != null && (Math.abs(finalProgress - lastVal).toFloat() / finalProgressMax.toFloat()) >= 0.05f

                val shouldThrottle = !isTerminalMilestone && !hasSignificantProgressDelta && (now - lastUpdate < PROGRESS_THROTTLE_MS)
                if (shouldThrottle) {
                    return@launch
                }
            }

            if (existing != null && isSameNotificationContext) {
                val hasNewTitle = finalTitle != null && finalTitle != existing.title
                val hasNewText = existing.text != finalHtmlText
                val hasNewImage = imagePath != null && imagePath != existing.imagePath
                val hasNewProgress = finalProgress != existing.progress ||
                        finalProgressMax != existing.progressMax ||
                        finalIsIndeterminate != existing.isIndeterminate

                if (hasNewTitle || hasNewText || hasNewImage || hasNewProgress) {
                    // Progress updates update row in-place without flooding history table
                    if (!isProgressUpdate && hasNewText && existing.text != null) {
                        database.notificationDao().insertHistory(
                            NotificationHistoryEntity(
                                notificationId = existing.id,
                                oldText = existing.text,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    database.notificationDao().update(
                        existing.copy(
                            title = finalTitle ?: existing.title,
                            text = finalHtmlText ?: existing.text,
                            timestamp = finalTimestamp,
                            isSystemRemoved = false,
                            imagePath = imagePath ?: existing.imagePath,
                            contentIntentUri = finalIntentUri ?: existing.contentIntentUri,
                            progress = finalProgress,
                            progressMax = finalProgressMax,
                            isIndeterminate = finalIsIndeterminate
                        )
                    )
                    if (isProgressUpdate) {
                        lastProgressUpdateTimestamps[sbnKey] = System.currentTimeMillis()
                        finalProgress?.let { lastProgressValues[sbnKey] = it }
                    }
                }
            } else {
                if (existing != null) {
                    database.notificationDao().markAsSystemRemovedById(existing.id)
                }
                database.notificationDao().insert(
                    NotificationEntity(
                        sbnKey = sbnKey,
                        packageName = packageName,
                        appName = appName,
                        title = finalTitle,
                        text = finalHtmlText,
                        timestamp = finalTimestamp,
                        imagePath = imagePath,
                        contentIntentUri = finalIntentUri,
                        progress = finalProgress,
                        progressMax = finalProgressMax,
                        isIndeterminate = finalIsIndeterminate
                    )
                )
                if (hasActiveProgressBar) {
                    lastProgressUpdateTimestamps[sbnKey] = System.currentTimeMillis()
                    finalProgress?.let { lastProgressValues[sbnKey] = it }
                }
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

    // Lazy picture extraction: stops at first available image source to minimize RAM allocation
    private fun extractNotificationPicture(notification: Notification, extras: Bundle): Bitmap? {
        // Priority 1: Direct EXTRA_PICTURE (BigPictureStyle)
        val rawPicture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        }
        if (rawPicture != null) {
            return scaleBitmapIfNeeded(rawPicture)
        }

        // Priority 2: android.pictureIcon (Android 12+ BigPictureStyle)
        @Suppress("DEPRECATION")
        val pictureIcon = (extras.get("android.pictureIcon") as? Icon)?.loadDrawable(this)?.toBitmap()
        if (pictureIcon != null) {
            return scaleBitmapIfNeeded(pictureIcon)
        }

        // Priority 3: MessagingStyle message attachments (WhatsApp, Telegram, etc.)
        val messagingPicture = extractMessagingStylePicture(extras)
        if (messagingPicture != null) {
            return messagingPicture
        }

        // Priority 4: Large Icon fallback
        @Suppress("DEPRECATION")
        val rawLargeIcon = extras.get(Notification.EXTRA_LARGE_ICON)
        return when (rawLargeIcon) {
            is Bitmap -> scaleBitmapIfNeeded(rawLargeIcon)
            is Icon -> rawLargeIcon.loadDrawable(this)?.toBitmap()?.let { scaleBitmapIfNeeded(it) }
            else -> notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()?.let { scaleBitmapIfNeeded(it) }
        }
    }

    // #5: Scale down large bitmaps to prevent ANR and memory pressure
    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap = ImageUtils.scaleBitmapIfNeeded(bitmap)

    // Inspect MessagingStyle message bundles for attached image URIs
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
                    msg.getParcelable("uri", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    msg.getParcelable<Uri>("uri")
                }
                if (uri != null) {
                    val sampled = decodeSampledBitmapFromUri(uri, ImageUtils.MAX_BITMAP_DIMENSION, ImageUtils.MAX_BITMAP_DIMENSION)
                    if (sampled != null) return sampled
                }
            }
        }
        return null
    }

    // Decode message attachments safely with inSampleSize and RGB_565 to prevent OOM
    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var inSampleSize = 1
            val height = boundsOptions.outHeight
            val width = boundsOptions.outWidth
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)
                decoded?.let { scaleBitmapIfNeeded(it) }
            }
        } catch (e: Exception) {
            SpeyeLogger.e("NotificationService", "Failed to decode sampled image from URI: $uri", e)
            null
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val sbnKey = sbn.key
        lastProgressUpdateTimestamps.remove(sbnKey)
        lastProgressValues.remove(sbnKey)
        lastMediaUpdateTimestamps.remove(sbnKey)
        lastMediaStates.remove(sbnKey)
        SpeyeLogger.d("NotificationService", "System removed: $sbnKey")
        serviceScope.launch {
            val existing = database.notificationDao().getActiveNotificationBySbnKey(sbnKey)
            if (existing != null && (existing.isMedia || existing.isVoiceMessage)) {
                // Freeze final state: mark system removed and paused, preserve position/duration/cover/audio
                database.notificationDao().update(
                    existing.copy(
                        isSystemRemoved = true,
                        mediaPlaybackState = android.media.session.PlaybackState.STATE_PAUSED
                    )
                )
            } else {
                database.notificationDao().markAsSystemRemoved(sbnKey)
            }
        }
    }
}
