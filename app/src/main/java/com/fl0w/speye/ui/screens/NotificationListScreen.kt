package com.fl0w.speye.ui.screens

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import coil.compose.AsyncImage
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.data.model.NotificationEntity
import com.fl0w.speye.data.model.NotificationWithHistory
import com.fl0w.speye.ui.viewmodel.NotificationViewModel
import com.fl0w.speye.utils.HtmlUtils
import com.fl0w.speye.utils.ImageUtils
import com.fl0w.speye.utils.SpeyeLogger
import com.fl0w.speye.data.settings.DebugSettingsManager

@Composable
fun NotificationListScreen(
    viewModel: NotificationViewModel, 
    onTestStandard: () -> Unit,
    onTestLongText: () -> Unit,
    onTestImage: () -> Unit
) {
    val groupedNotifications by viewModel.groupedNotifications.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val context = LocalContext.current
    val debugManager = remember { DebugSettingsManager(context) }
    val isTestButtonEnabled by debugManager.isTestButtonEnabled.collectAsState(initial = false)
    val isAccordionEnabled by debugManager.isAccordionEnabled.collectAsState(initial = false)

    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var showTestMenu by remember { mutableStateOf(false) }
    var viewerImagePath by remember { mutableStateOf<String?>(null) }
    var isSearchMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
        ) {
            // Delete All (Top Left)
            IconButton(
                onClick = { deleteTarget = DeleteTarget.All },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_all), tint = SpeyeTheme.colors.textSecondary)
            }

            // Logo & Title (Center)
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    SpeyeIcons.Logo,
                    contentDescription = null,
                    modifier = Modifier.size(width = 52.dp, height = 31.dp),
                    tint = Color.White
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    "SPEYE",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            // Test & Search (Top Right)
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTestButtonEnabled) {
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpeyeTheme.colors.surface)
                                .clickable { showTestMenu = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.test), color = SpeyeTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))
                        ) {
                            com.fl0w.speye.ui.components.SpeyeDropdownMenu(
                                expanded = showTestMenu,
                                onDismissRequest = { showTestMenu = false },
                                modifier = Modifier.background(SpeyeTheme.colors.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.test_cycle), color = SpeyeTheme.colors.textPrimary) },
                                    onClick = { onTestStandard(); showTestMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.test_long_text), color = SpeyeTheme.colors.textPrimary) },
                                    onClick = { onTestLongText(); showTestMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.test_image), color = SpeyeTheme.colors.textPrimary) },
                                    onClick = { onTestImage(); showTestMenu = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = { isSearchMode = true }) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SpeyeTheme.colors.textSecondary)
                }
            }
        }

        // Pop-up Search Bar
        val reduceMotion = SpeyeTheme.reduceMotion
        if (reduceMotion) {
            if (isSearchMode) {
                androidx.compose.material3.TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SpeyeTheme.colors.primary, RoundedCornerShape(12.dp)),
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = SpeyeTheme.colors.textSecondary) },
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
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = SpeyeTheme.colors.primary,
                        focusedTextColor = SpeyeTheme.colors.textPrimary,
                        unfocusedTextColor = SpeyeTheme.colors.textPrimary
                    ),
                    singleLine = true
                )
            }
        } else {
            AnimatedVisibility(
                visible = isSearchMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                androidx.compose.material3.TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SpeyeTheme.colors.primary, RoundedCornerShape(12.dp)),
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = SpeyeTheme.colors.textSecondary) },
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
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = SpeyeTheme.colors.primary,
                        focusedTextColor = SpeyeTheme.colors.textPrimary,
                        unfocusedTextColor = SpeyeTheme.colors.textPrimary
                    ),
                    singleLine = true
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SpeyeTheme.colors.divider)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (groupedNotifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = SpeyeTheme.colors.divider
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isEmpty()) stringResource(R.string.no_notifications) 
                            else stringResource(R.string.no_results), 
                            color = SpeyeTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groupedNotifications.forEach { (packageName, notifications) ->
                        val isExpanded = expandedGroups.contains(packageName)
                        
                        item(key = "header_$packageName") {
                            var showHeaderMenu by remember { mutableStateOf(false) }

                            Box {
                                AppGroupHeader(
                                    packageName = packageName,
                                    appName = notifications.firstOrNull()?.notification?.appName,
                                    count = notifications.size,
                                    isExpanded = isExpanded,
                                    onToggle = { viewModel.toggleGroup(packageName) },
                                    onLongPress = { showHeaderMenu = true }
                                )

                                MaterialTheme(
                                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                                ) {
                                    com.fl0w.speye.ui.components.SpeyeDropdownMenu(
                                        expanded = showHeaderMenu,
                                        onDismissRequest = { showHeaderMenu = false },
                                        modifier = Modifier.background(SpeyeTheme.colors.surface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete_all), color = SpeyeTheme.colors.error) },
                                            onClick = {
                                                deleteTarget = DeleteTarget.Group(packageName)
                                                showHeaderMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = SpeyeTheme.colors.error) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.dismiss), color = SpeyeTheme.colors.textSecondary) },
                                            onClick = {
                                                viewModel.ignoreApp(packageName)
                                                showHeaderMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Default.NotificationsOff, null, tint = SpeyeTheme.colors.textSecondary) }
                                        )
                                    }
                                }
                            }
                        }

                        // Hybrid Rendering: Use AnimatedVisibility if enabled in Debug Menu and reduceMotion is false,
                        // otherwise use raw items() for maximum performance and motion reduction.
                        if (isAccordionEnabled && !reduceMotion) {
                            item(key = "content_$packageName") {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        notifications.forEach { item ->
                                            NotificationItemWrapper(
                                                item = item,
                                                context = context,
                                                onImageClick = { path -> viewerImagePath = path },
                                                onDeleteClick = { deleteTarget = DeleteTarget.Item(item.notification.id) },
                                                onIgnoreApp = { viewModel.ignoreApp(item.notification.packageName) }
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (isExpanded) {
                            items(
                                items = notifications,
                                key = { it.notification.id }
                            ) { item ->
                                Box(modifier = if (reduceMotion) Modifier else Modifier.animateItem()) {
                                    NotificationItemWrapper(
                                        item = item,
                                        context = context,
                                        onImageClick = { path -> viewerImagePath = path },
                                        onDeleteClick = { deleteTarget = DeleteTarget.Item(item.notification.id) },
                                        onIgnoreApp = { viewModel.ignoreApp(item.notification.packageName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        com.fl0w.speye.FlavorDelegateImpl.AdBanner()
    }

    deleteTarget?.let { target ->
        CustomDeleteDialog(
            target = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                when (target) {
                    is DeleteTarget.All -> viewModel.deleteAll()
                    is DeleteTarget.Group -> viewModel.deleteGroup(target.packageName)
                    is DeleteTarget.Item -> viewModel.deleteNotification(target.id)
                }
                deleteTarget = null
            }
        )
    }

    viewerImagePath?.let { path ->
        ImageViewerDialog(
            imagePath = path,
            onDismiss = { viewerImagePath = null },
            onExport = { ImageUtils.exportToGallery(context, path) }
        )
    }
}

@Composable
fun NotificationItemWrapper(
    item: NotificationWithHistory,
    context: Context,
    onImageClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onIgnoreApp: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Box {
        NotificationItem(
            item = item,
            onLongPress = { showMenu = true },
            onImageClick = onImageClick
        )

        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
        ) {
            com.fl0w.speye.ui.components.SpeyeDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SpeyeTheme.colors.surface)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.go), color = SpeyeTheme.colors.primary) },
                    onClick = {
                        launchNotificationIntent(context, item.notification)
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = SpeyeTheme.colors.primary) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_content), color = SpeyeTheme.colors.textPrimary) },
                    onClick = {
                        val rawText = item.notification.text ?: ""
                        val plainText = HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                        clipboardManager.setText(AnnotatedString(plainText))
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = SpeyeTheme.colors.primary) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = SpeyeTheme.colors.error) },
                    onClick = {
                        onDeleteClick()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = SpeyeTheme.colors.error) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dismiss), color = SpeyeTheme.colors.textSecondary) },
                    onClick = {
                        onIgnoreApp()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.NotificationsOff, null, tint = SpeyeTheme.colors.textSecondary) }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(SpeyeTheme.colors.divider)
    )
}

@Composable
fun ImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = imagePath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpeyeTheme.colors.primary)
                            .clickable { onExport() }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_to_gallery), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDeleteDialog(
    target: DeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SpeyeTheme.colors.surface)
                .padding(24.dp)
        ) {
            Column {
                Text(
                    stringResource(R.string.confirm_deletion),
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    when (target) {
                        is DeleteTarget.All -> stringResource(R.string.confirm_delete_all)
                        is DeleteTarget.Group -> stringResource(R.string.confirm_delete_group)
                        is DeleteTarget.Item -> stringResource(R.string.confirm_delete_item)
                    },
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.cancel), color = SpeyeTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpeyeTheme.colors.error.copy(alpha = 0.1f))
                            .clickable { onConfirm() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.delete), color = SpeyeTheme.colors.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

sealed class DeleteTarget {
    object All : DeleteTarget()
    data class Group(val packageName: String) : DeleteTarget()
    data class Item(val id: Long) : DeleteTarget()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGroupHeader(
    packageName: String,
    appName: String?,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isExpanded) SpeyeTheme.colors.surface else SpeyeTheme.colors.background)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appIcon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = appName ?: packageName,
                        color = SpeyeTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = packageName,
                        fontSize = 10.sp,
                        color = SpeyeTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(R.string.notification_count, count),
                    fontSize = 12.sp,
                    color = SpeyeTheme.colors.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = SpeyeTheme.colors.textSecondary
            )
        }
    }
}

fun launchNotificationIntent(context: Context, notification: NotificationEntity) {
    try {
        val uri = notification.contentIntentUri
        if (uri != null) {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            // Fallback: Open the app
            val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    } catch (e: Exception) {
        SpeyeLogger.e("NotificationList", "Failed to launch intent", e)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    item: NotificationWithHistory, 
    onLongPress: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val notification = item.notification
    var isHistoryExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (item.history.isNotEmpty()) isHistoryExpanded = !isHistoryExpanded },
                onLongClick = onLongPress
            )
            .padding(16.dp)
            .alpha(if (notification.isSystemRemoved) 0.5f else 1.0f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (notification.isSystemRemoved) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpeyeTheme.colors.divider)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.dismissed), fontSize = 9.sp, color = SpeyeTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = DateUtils.getRelativeTimeSpanString(notification.timestamp).toString().uppercase(),
                    fontSize = 10.sp,
                    color = SpeyeTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = notification.title ?: stringResource(R.string.no_title),
                color = SpeyeTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (item.history.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SpeyeTheme.colors.secondary.copy(alpha = 0.1f))
                        .clickable { isHistoryExpanded = !isHistoryExpanded }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.edited, item.history.size), color = SpeyeTheme.colors.secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            if (isHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = SpeyeTheme.colors.secondary
                        )
                    }
                }
            }
        }
        val contentText = notification.text ?: ""
        val annotatedText = remember(contentText) {
            HtmlUtils.fromHtmlToAnnotatedString(contentText)
        }

        Text(
            text = annotatedText,
            fontSize = 14.sp,
            color = if (item.history.isNotEmpty()) SpeyeTheme.colors.secondary else SpeyeTheme.colors.textSecondary
        )

        notification.imagePath?.let { path ->
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpeyeTheme.colors.surface)
                    .clickable { onImageClick(path) }
            )
        }

        val reduceMotion = SpeyeTheme.reduceMotion
        if (reduceMotion) {
            if (isHistoryExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(SpeyeTheme.colors.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(stringResource(R.string.history), fontSize = 10.sp, fontWeight = FontWeight.Black, color = SpeyeTheme.colors.primary)
                    item.history.reversed().forEach { historyItem ->
                        val historyContent = historyItem.oldText ?: ""
                        val renderedHistory = remember(historyContent) {
                            HtmlUtils.fromHtmlToAnnotatedString(historyContent)
                        }
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = renderedHistory,
                                fontSize = 13.sp,
                                color = SpeyeTheme.colors.textSecondary,
                                fontStyle = FontStyle.Italic
                            )
                            Text(
                                text = DateUtils.getRelativeTimeSpanString(historyItem.timestamp).toString(),
                                fontSize = 9.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        } else {
            AnimatedVisibility(visible = isHistoryExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(SpeyeTheme.colors.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(stringResource(R.string.history), fontSize = 10.sp, fontWeight = FontWeight.Black, color = SpeyeTheme.colors.primary)
                    item.history.reversed().forEach { historyItem ->
                        val historyContent = historyItem.oldText ?: ""
                        val renderedHistory = remember(historyContent) {
                            HtmlUtils.fromHtmlToAnnotatedString(historyContent)
                        }
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = renderedHistory,
                                fontSize = 13.sp,
                                color = SpeyeTheme.colors.textSecondary,
                                fontStyle = FontStyle.Italic
                            )
                            Text(
                                text = DateUtils.getRelativeTimeSpanString(historyItem.timestamp).toString(),
                                fontSize = 9.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}
