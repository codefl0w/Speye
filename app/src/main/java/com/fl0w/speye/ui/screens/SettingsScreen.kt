package com.fl0w.speye.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.utils.BackupManager
import com.fl0w.speye.data.settings.DebugSettingsManager
import com.fl0w.speye.utils.SpeyeLogger
import com.fl0w.speye.ui.components.SettingsSection
import com.fl0w.speye.ui.components.SettingsItem
import com.fl0w.speye.ui.components.SettingsToggleItem
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.fl0w.speye.data.settings.AppSettingsManager
import com.fl0w.speye.data.settings.ImageFormatSetting
import com.fl0w.speye.ui.components.SpeyeDropdownMenu

@Composable
fun SettingsScreen(
    onNavigateToAccessibility: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToGoogleDrive: () -> Unit,
    onNavigateToFaq: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onTestMedia: () -> Unit = {},
    onTestVoiceMessage: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debugManager = remember { DebugSettingsManager(context) }
    val appSettings = remember { AppSettingsManager(context) }
    
    val isDebugMode by debugManager.isDebugModeEnabled.collectAsState(initial = false)
    val isTestButtonEnabled by debugManager.isTestButtonEnabled.collectAsState(initial = false)
    val isLoggingEnabled by debugManager.isLoggingEnabled.collectAsState(initial = false)
    val isAccordionEnabled by debugManager.isAccordionEnabled.collectAsState(initial = false)
    val currentFormat by appSettings.imageFormat.collectAsState(initial = ImageFormatSetting.PNG)
    val currentRetentionDays by appSettings.retentionDays.collectAsState(initial = 0)

    var expandedFormat by remember { mutableStateOf(false) }
    var expandedRetention by remember { mutableStateOf(false) }

    val formatLabels = mapOf(
        ImageFormatSetting.PNG to stringResource(R.string.format_png),
        ImageFormatSetting.JPEG_HIGH to stringResource(R.string.format_jpeg_high),
        ImageFormatSetting.JPEG_BALANCED to stringResource(R.string.format_jpeg_medium),
        ImageFormatSetting.WEBP to stringResource(R.string.format_webp)
    )

    val retentionLabels = mapOf(
        0 to stringResource(R.string.retention_forever),
        30 to stringResource(R.string.retention_30_days),
        60 to stringResource(R.string.retention_60_days),
        90 to stringResource(R.string.retention_90_days),
        180 to stringResource(R.string.retention_180_days)
    )

    val debugModeEnabledMsg = stringResource(R.string.debug_mode_enabled)
    val exportSuccessMsg = stringResource(R.string.export_success)
    val exportFailedMsg = stringResource(R.string.export_failed)
    val importSuccessMsg = stringResource(R.string.import_success)
    val importFailedMsg = stringResource(R.string.import_failed)
    val feedbackSubject = stringResource(R.string.feedback_subject)
    val noEmailAppMsg = stringResource(R.string.no_email_app)

    var logoClickCount by remember { mutableStateOf(0) }
    var lastLogoClickTime by remember { mutableLongStateOf(0L) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        BackupManager.exportData(context, it)
                        Toast.makeText(context, exportSuccessMsg, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, exportFailedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        BackupManager.importData(context, it)
                        Toast.makeText(context, importSuccessMsg, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, importFailedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Center Logo Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    SpeyeIcons.Logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 150.dp, height = 90.dp)
                        .clickable(interactionSource = null, indication = null) {
                            val now = System.currentTimeMillis()
                            if (now - lastLogoClickTime < 500) {
                                logoClickCount++
                            } else {
                                logoClickCount = 1
                            }
                            lastLogoClickTime = now
                            if (logoClickCount >= 5) {
                                scope.launch { debugManager.setDebugModeEnabled(true) }
                                logoClickCount = 0
                                Toast.makeText(context, debugModeEnabledMsg, Toast.LENGTH_SHORT).show()
                            }
                        },
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "SPEYE",
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "v${com.fl0w.speye.BuildConfig.VERSION_NAME}",
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
                com.fl0w.speye.FlavorDelegateImpl.ProCrownBadge()
            }
        }

        com.fl0w.speye.FlavorDelegateImpl.ProVersionHeader()
        com.fl0w.speye.FlavorDelegateImpl.RestorePurchasesItem()

        if (isDebugMode) {
            SettingsSection(title = stringResource(R.string.debug_menu)) {
                com.fl0w.speye.FlavorDelegateImpl.DebugProToggle()
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.disable_debug),
                    subtitle = stringResource(R.string.disable_debug_sub),
                    onClick = { scope.launch { debugManager.setDebugModeEnabled(false) } }
                )
                SettingsToggleItem(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.show_test_button),
                    subtitle = stringResource(R.string.show_test_button_sub),
                    checked = isTestButtonEnabled,
                    onCheckedChange = { scope.launch { debugManager.setTestButtonEnabled(it) } }
                )
                SettingsToggleItem(
                    icon = Icons.Default.Animation,
                    title = stringResource(R.string.enable_accordion),
                    subtitle = stringResource(R.string.enable_accordion_sub),
                    checked = isAccordionEnabled,
                    onCheckedChange = { scope.launch { debugManager.setAccordionEnabled(it) } }
                )
                SettingsToggleItem(
                    icon = Icons.Default.Terminal,
                    title = stringResource(R.string.enable_logging),
                    subtitle = stringResource(R.string.enable_logging_sub),
                    checked = isLoggingEnabled,
                    onCheckedChange = { 
                        scope.launch { 
                            debugManager.setLoggingEnabled(it)
                            SpeyeLogger.setLoggingEnabled(it)
                        } 
                    }
                )
                SettingsItem(
                    icon = Icons.Default.HistoryEdu,
                    title = stringResource(R.string.view_logs),
                    subtitle = stringResource(R.string.view_logs_sub),
                    onClick = onNavigateToLogs
                )
                SettingsItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Test Media Playback",
                    subtitle = "Play test ringtone with interactive media notification",
                    onClick = onTestMedia
                )
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = "Test Voice Message",
                    subtitle = "Simulate incoming voice message notification with audio",
                    onClick = onTestVoiceMessage
                )
            }
        }

        SettingsSection(title = stringResource(R.string.data_management)) {
            Box {
                SettingsItem(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.image_quality_title),
                    subtitle = formatLabels[currentFormat] ?: currentFormat.name,
                    onClick = { expandedFormat = true }
                )
                SpeyeDropdownMenu(
                    expanded = expandedFormat,
                    onDismissRequest = { expandedFormat = false },
                    modifier = Modifier.background(SpeyeTheme.colors.surface)
                ) {
                    com.fl0w.speye.data.settings.ImageFormatSetting.entries.forEach { format ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(formatLabels[format] ?: format.name, color = if (format == currentFormat) SpeyeTheme.colors.primary else SpeyeTheme.colors.textPrimary) },
                            onClick = {
                                scope.launch { appSettings.setImageFormat(format) }
                                expandedFormat = false
                            }
                        )
                    }
                }
            }

            Box {
                SettingsItem(
                    icon = Icons.Default.AutoDelete,
                    title = stringResource(R.string.retention_title),
                    subtitle = retentionLabels[currentRetentionDays] ?: stringResource(R.string.retention_forever),
                    onClick = { expandedRetention = true }
                )
                SpeyeDropdownMenu(
                    expanded = expandedRetention,
                    onDismissRequest = { expandedRetention = false },
                    modifier = Modifier.background(SpeyeTheme.colors.surface)
                ) {
                    listOf(0, 30, 60, 90, 180).forEach { days ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(retentionLabels[days] ?: "$days days", color = if (days == currentRetentionDays) SpeyeTheme.colors.primary else SpeyeTheme.colors.textPrimary) },
                            onClick = {
                                scope.launch { appSettings.setRetentionDays(days) }
                                expandedRetention = false
                            }
                        )
                    }
                }
            }

            SettingsItem(
                icon = Icons.Default.FileUpload,
                title = stringResource(R.string.export_history),
                subtitle = stringResource(R.string.export_history_sub),
                onClick = {
                    val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                    exportLauncher.launch("SpeyeBackup-$date.spy")
                }
            )
            SettingsItem(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.import_history),
                subtitle = stringResource(R.string.import_history_sub),
                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/zip")) }
            )
            com.fl0w.speye.FlavorDelegateImpl.GoogleDriveSettingsItem(onNavigate = onNavigateToGoogleDrive)
        }

        SettingsSection(title = stringResource(R.string.accessibility_title)) {
            SettingsItem(
                icon = Icons.Default.AccessibilityNew,
                title = stringResource(R.string.accessibility_title),
                subtitle = stringResource(R.string.accessibility_sub),
                onClick = onNavigateToAccessibility
            )
        }

        SettingsSection(title = stringResource(R.string.about)) {
            SettingsItem(
                icon = Icons.Default.QuestionAnswer,
                title = stringResource(R.string.faq),
                subtitle = stringResource(R.string.faq_sub),
                onClick = onNavigateToFaq
            )
            SettingsItem(
                icon = Icons.Default.Email,
                title = stringResource(R.string.contact_developer),
                subtitle = "fl0w_dev@protonmail.com",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:fl0w_dev@protonmail.com")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, feedbackSubject)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, noEmailAppMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.privacy_policy_sub),
                onClick = onNavigateToPrivacyPolicy
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
