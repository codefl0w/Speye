package com.fl0w.speye.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.ui.viewmodel.AppFilter
import com.fl0w.speye.ui.viewmodel.AppInfo
import com.fl0w.speye.ui.viewmodel.AppListViewModel
import com.fl0w.speye.ui.viewmodel.GranularFilterType

@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val apps by viewModel.apps.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val expandedApps by viewModel.expandedApps.collectAsState()
    var isSearchMode by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSearchMode) {
        isSearchMode = false
        viewModel.setSearchQuery("")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
        ) {
            Icon(
                SpeyeIcons.Logo,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 52.dp, height = 31.dp)
                    .align(Alignment.Center),
                tint = Color.White
            )

            IconButton(
                onClick = { isSearchMode = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = SpeyeTheme.colors.textSecondary
                )
            }
        }

        // Pop-up Search Bar
        val reduceMotion = SpeyeTheme.reduceMotion
        if (reduceMotion) {
            if (isSearchMode) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SpeyeTheme.colors.primary, RoundedCornerShape(12.dp)),
                    placeholder = {
                        Text(
                            stringResource(R.string.search_apps_placeholder),
                            color = SpeyeTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        IconButton(onClick = {
                            isSearchMode = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SpeyeTheme.colors.textPrimary)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, null, tint = SpeyeTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedTextColor = SpeyeTheme.colors.textPrimary,
                        unfocusedTextColor = SpeyeTheme.colors.textPrimary,
                        cursorColor = SpeyeTheme.colors.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        } else {
            AnimatedVisibility(
                visible = isSearchMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SpeyeTheme.colors.primary, RoundedCornerShape(12.dp)),
                    placeholder = {
                        Text(
                            stringResource(R.string.search_apps_placeholder),
                            color = SpeyeTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        IconButton(onClick = {
                            isSearchMode = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SpeyeTheme.colors.textPrimary)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, null, tint = SpeyeTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedTextColor = SpeyeTheme.colors.textPrimary,
                        unfocusedTextColor = SpeyeTheme.colors.textPrimary,
                        cursorColor = SpeyeTheme.colors.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            stringResource(R.string.app_list_description),
            color = SpeyeTheme.colors.textSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Tags
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppFilterTag(
                label = stringResource(R.string.filter_all),
                isSelected = currentFilter == AppFilter.ALL,
                onClick = { viewModel.setFilter(AppFilter.ALL) }
            )
            AppFilterTag(
                label = stringResource(R.string.filter_user),
                isSelected = currentFilter == AppFilter.USER,
                onClick = { viewModel.setFilter(AppFilter.USER) }
            )
            AppFilterTag(
                label = stringResource(R.string.filter_system),
                isSelected = currentFilter == AppFilter.SYSTEM,
                onClick = { viewModel.setFilter(AppFilter.SYSTEM) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SpeyeTheme.colors.divider)
        )

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_results),
                    color = SpeyeTheme.colors.textSecondary
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    val isExpanded = expandedApps.contains(app.packageName)
                    AppItem(
                        app = app,
                        isExpanded = isExpanded,
                        onRowClick = { viewModel.toggleExpand(app.packageName) },
                        onMainToggle = { viewModel.toggleIgnore(app.packageName, !app.isMainToggleOn) },
                        onGranularChange = { type, enabled ->
                            viewModel.setGranularFilter(app.packageName, type, enabled)
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(0.5.dp)
                            .background(SpeyeTheme.colors.divider)
                    )
                }
            }
        }
    }
}

@Composable
fun AppFilterTag(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SpeyeTheme.colors.primary.copy(alpha = 0.2f) else SpeyeTheme.colors.surface)
            .then(
                if (isSelected) Modifier.border(1.dp, SpeyeTheme.colors.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) SpeyeTheme.colors.primary else SpeyeTheme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
        )
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    isExpanded: Boolean,
    onRowClick: () -> Unit,
    onMainToggle: () -> Unit,
    onGranularChange: (GranularFilterType, Boolean) -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        com.fl0w.speye.utils.AppIconCache.getIcon(context, app.packageName)
    }
    val reduceMotion = SpeyeTheme.reduceMotion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isExpanded) SpeyeTheme.colors.surface.copy(alpha = 0.6f) else SpeyeTheme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRowClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appIcon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = SpeyeTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Mini Hint Icons Row: Full White when ON, Reddish with diagonal strike-through when OFF
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppHintIcon(
                        icon = Icons.Default.TextFields,
                        enabled = app.allowText,
                        contentDescription = stringResource(R.string.filter_type_text)
                    )
                    AppHintIcon(
                        icon = Icons.Default.Image,
                        enabled = app.allowLazyAssets,
                        contentDescription = stringResource(R.string.filter_type_lazy_assets)
                    )
                    AppHintIcon(
                        icon = Icons.Default.GifBox,
                        enabled = app.allowStickers,
                        contentDescription = stringResource(R.string.filter_type_stickers)
                    )
                    AppHintIcon(
                        icon = Icons.Default.MusicNote,
                        enabled = app.allowMedia,
                        contentDescription = stringResource(R.string.filter_type_media)
                    )
                    AppHintIcon(
                        icon = Icons.Default.Mic,
                        enabled = app.allowVoice,
                        contentDescription = stringResource(R.string.filter_type_voice)
                    )
                    AppHintIcon(
                        icon = Icons.Default.Download,
                        enabled = app.allowProgress,
                        contentDescription = stringResource(R.string.filter_type_progress)
                    )
                    AppHintIcon(
                        icon = Icons.Default.TouchApp,
                        enabled = app.allowActions,
                        contentDescription = stringResource(R.string.filter_type_actions)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = SpeyeTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            com.fl0w.speye.ui.components.SpeyeSwitch(
                checked = app.isMainToggleOn,
                onCheckedChange = { onMainToggle() }
            )
        }

        if (reduceMotion) {
            if (isExpanded) {
                GranularFilterPanel(app = app, onGranularChange = onGranularChange)
            }
        } else {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                GranularFilterPanel(app = app, onGranularChange = onGranularChange)
            }
        }
    }
}

@Composable
fun AppHintIcon(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String?
) {
    val errorColor = SpeyeTheme.colors.error
    val tintColor = if (enabled) Color.White else errorColor
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (enabled) Color.White.copy(alpha = 0.08f)
                else errorColor.copy(alpha = 0.15f)
            )
            .drawWithContent {
                drawContent()
                if (!enabled) {
                    drawLine(
                        color = errorColor,
                        start = Offset(size.width * 0.18f, size.height * 0.82f),
                        end = Offset(size.width * 0.82f, size.height * 0.18f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun GranularFilterPanel(
    app: AppInfo,
    onGranularChange: (GranularFilterType, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SpeyeTheme.colors.surface)
            .border(1.dp, SpeyeTheme.colors.divider, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp)
    ) {
        GranularFilterOptionRow(
            icon = Icons.Default.TextFields,
            title = stringResource(R.string.filter_type_text),
            subtitle = stringResource(R.string.filter_type_text_sub),
            checked = app.allowText,
            onCheckedChange = { onGranularChange(GranularFilterType.TEXT, it) }
        )
        GranularDivider()
        GranularFilterOptionRow(
            icon = Icons.Default.Image,
            title = stringResource(R.string.filter_type_lazy_assets),
            subtitle = stringResource(R.string.filter_type_lazy_assets_sub),
            checked = app.allowLazyAssets,
            onCheckedChange = { onGranularChange(GranularFilterType.LAZY_ASSETS, it) }
        )
        GranularDivider()
        GranularFilterOptionRow(
            icon = Icons.Default.GifBox,
            title = stringResource(R.string.filter_type_stickers),
            subtitle = stringResource(R.string.filter_type_stickers_sub),
            checked = app.allowStickers,
            onCheckedChange = { onGranularChange(GranularFilterType.STICKERS, it) }
        )
        GranularDivider()
        GranularFilterOptionRow(
            icon = Icons.Default.MusicNote,
            title = stringResource(R.string.filter_type_media),
            subtitle = stringResource(R.string.filter_type_media_sub),
            checked = app.allowMedia,
            onCheckedChange = { onGranularChange(GranularFilterType.MEDIA, it) }
        )
        GranularDivider()
        GranularFilterOptionRow(
            icon = Icons.Default.Mic,
            title = stringResource(R.string.filter_type_voice),
            subtitle = stringResource(R.string.filter_type_voice_sub),
            checked = app.allowVoice,
            onCheckedChange = { onGranularChange(GranularFilterType.VOICE, it) }
        )
        GranularDivider()
        GranularFilterOptionRow(
            icon = Icons.Default.Download,
            title = stringResource(R.string.filter_type_progress),
            subtitle = stringResource(R.string.filter_type_progress_sub),
            checked = app.allowProgress,
            onCheckedChange = { onGranularChange(GranularFilterType.PROGRESS, it) }
        )
        GranularDivider()
        GranularFilterOptionRow(
            icon = Icons.Default.TouchApp,
            title = stringResource(R.string.filter_type_actions),
            subtitle = stringResource(R.string.filter_type_actions_sub),
            checked = app.allowActions,
            onCheckedChange = { onGranularChange(GranularFilterType.ACTIONS, it) }
        )
    }
}

@Composable
private fun GranularDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(0.5.dp)
            .background(SpeyeTheme.colors.divider)
    )
}

@Composable
private fun GranularFilterOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppHintIcon(
            icon = icon,
            enabled = checked,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (checked) SpeyeTheme.colors.textPrimary else SpeyeTheme.colors.error,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = SpeyeTheme.colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        com.fl0w.speye.ui.components.SpeyeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
