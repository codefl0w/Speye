package com.fl0w.speye.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.data.settings.AppSettingsManager
import com.fl0w.speye.ui.components.SettingsItem
import com.fl0w.speye.ui.components.SettingsSection
import com.fl0w.speye.ui.components.SettingsToggleItem
import kotlinx.coroutines.launch

@Composable
fun AccessibilityScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSettings = remember { AppSettingsManager(context) }

    val currentTextScale by appSettings.textScale.collectAsState(initial = 1.0f)
    val isHighContrast by appSettings.highContrast.collectAsState(initial = false)
    val isReduceAnimations by appSettings.reduceAnimations.collectAsState(initial = false)

    var expandedTextSize by remember { mutableStateOf(false) }

    val textSizeLabels = mapOf(
        1.0f to stringResource(R.string.text_size_default),
        1.15f to stringResource(R.string.text_size_large),
        1.30f to stringResource(R.string.text_size_xlarge)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeyeTheme.colors.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpeyeTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.accessibility_title),
                color = SpeyeTheme.colors.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = stringResource(R.string.accessibility_section_display)) {
                Box {
                    SettingsItem(
                        icon = Icons.Default.FormatSize,
                        title = stringResource(R.string.text_size_title),
                        subtitle = textSizeLabels[currentTextScale] ?: "${(currentTextScale * 100).toInt()}%",
                        onClick = { expandedTextSize = true }
                    )
                    com.fl0w.speye.ui.components.SpeyeDropdownMenu(
                        expanded = expandedTextSize,
                        onDismissRequest = { expandedTextSize = false },
                        modifier = Modifier.background(SpeyeTheme.colors.surface)
                    ) {
                        listOf(1.0f, 1.15f, 1.30f).forEach { scale ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = textSizeLabels[scale] ?: "${(scale * 100).toInt()}%",
                                        color = if (scale == currentTextScale) SpeyeTheme.colors.primary else SpeyeTheme.colors.textPrimary
                                    )
                                },
                                onClick = {
                                    scope.launch { appSettings.setTextScale(scale) }
                                    expandedTextSize = false
                                }
                            )
                        }
                    }
                }

                // Live Preview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpeyeTheme.colors.surface)
                        .border(1.dp, SpeyeTheme.colors.divider, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.accessibility_preview_app),
                                color = SpeyeTheme.colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "12:45 PM",
                                color = SpeyeTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.accessibility_preview_title),
                            color = SpeyeTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.accessibility_preview_desc),
                            color = SpeyeTheme.colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.accessibility_section_vision)) {
                SettingsToggleItem(
                    icon = Icons.Default.Contrast,
                    title = stringResource(R.string.high_contrast_title),
                    subtitle = stringResource(R.string.high_contrast_sub),
                    checked = isHighContrast,
                    onCheckedChange = { scope.launch { appSettings.setHighContrast(it) } }
                )

                SettingsToggleItem(
                    icon = Icons.Default.MotionPhotosOff,
                    title = stringResource(R.string.reduce_motion_title),
                    subtitle = stringResource(R.string.reduce_motion_sub),
                    checked = isReduceAnimations,
                    onCheckedChange = { scope.launch { appSettings.setReduceAnimations(it) } }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
