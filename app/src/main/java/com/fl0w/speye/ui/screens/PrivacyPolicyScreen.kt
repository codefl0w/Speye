package com.fl0w.speye.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fl0w.speye.BuildConfig
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.ui.components.SettingsSection

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Custom Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp, start = 8.dp, end = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = SpeyeTheme.colors.textPrimary)
            }

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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    stringResource(R.string.privacy_title),
                    color = SpeyeTheme.colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.privacy_data_collection)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.privacy_local_storage),
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_local_storage_text),
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    stringResource(R.string.privacy_third_party),
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_third_party_text),
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    stringResource(R.string.privacy_cloud),
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_cloud_text),
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Permission Diagnostics
        SettingsSection(title = stringResource(R.string.perm_transparency_title)) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            var isNotifAccessGranted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var isPostNotifGranted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var isBatteryExempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            val updatePermissions = {
                isNotifAccessGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                isPostNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                isBatteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }

            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        updatePermissions()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                updatePermissions()
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val isFoss = BuildConfig.FLAVOR.equals("foss", ignoreCase = true)

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionDetailCard(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.perm_notif_access),
                    isGranted = isNotifAccessGranted,
                    statusText = if (isNotifAccessGranted) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                    howText = stringResource(R.string.perm_notif_access_how),
                    whyText = stringResource(R.string.perm_notif_access_why),
                    whereText = stringResource(R.string.perm_notif_access_where),
                    handlingText = stringResource(R.string.perm_notif_access_handling)
                )

                PermissionDetailCard(
                    icon = Icons.Default.NotificationAdd,
                    title = stringResource(R.string.perm_post_notif),
                    isGranted = isPostNotifGranted,
                    statusText = if (isPostNotifGranted) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                    howText = stringResource(R.string.perm_post_notif_how),
                    whyText = stringResource(R.string.perm_post_notif_why),
                    whereText = stringResource(R.string.perm_post_notif_where),
                    handlingText = stringResource(R.string.perm_post_notif_handling)
                )

                PermissionDetailCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.perm_background),
                    isGranted = isBatteryExempt,
                    statusText = if (isBatteryExempt) stringResource(R.string.whitelisted) else stringResource(R.string.not_whitelisted),
                    howText = stringResource(R.string.perm_battery_how),
                    whyText = stringResource(R.string.perm_battery_why),
                    whereText = stringResource(R.string.perm_battery_where),
                    handlingText = stringResource(R.string.perm_battery_handling)
                )

                PermissionDetailCard(
                    icon = if (isFoss) Icons.Default.WifiOff else Icons.Default.Wifi,
                    title = stringResource(R.string.perm_network_title),
                    isGranted = true,
                    statusText = if (isFoss) stringResource(R.string.offline_zero_perms) else stringResource(R.string.cloud_ads_only),
                    howText = stringResource(if (isFoss) R.string.perm_network_foss_how else R.string.perm_network_play_how),
                    whyText = stringResource(if (isFoss) R.string.perm_network_foss_why else R.string.perm_network_play_why),
                    whereText = stringResource(if (isFoss) R.string.perm_network_foss_where else R.string.perm_network_play_where),
                    handlingText = stringResource(if (isFoss) R.string.perm_network_foss_handling else R.string.perm_network_play_handling)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionDetailCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isGranted: Boolean,
    statusText: String,
    howText: String,
    whyText: String,
    whereText: String,
    handlingText: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(SpeyeTheme.colors.surface)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = SpeyeTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        title,
                        color = SpeyeTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(if (isGranted) Color(0xFF1E3A24) else Color(0xFF3A241E))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            color = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PermissionFactRow(label = stringResource(R.string.why_label), desc = whyText)
            Spacer(modifier = Modifier.height(6.dp))
            PermissionFactRow(label = stringResource(R.string.how_label), desc = howText)
            Spacer(modifier = Modifier.height(6.dp))
            PermissionFactRow(label = stringResource(R.string.where_label), desc = whereText)
            Spacer(modifier = Modifier.height(6.dp))
            PermissionFactRow(label = stringResource(R.string.handling_label), desc = handlingText)
        }
    }
}

@Composable
private fun PermissionFactRow(label: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            color = SpeyeTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            color = SpeyeTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
