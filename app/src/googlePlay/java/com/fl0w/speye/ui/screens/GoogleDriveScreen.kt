package com.fl0w.speye.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.ui.components.SettingsSection
import com.fl0w.speye.ui.components.SettingsItem
import com.fl0w.speye.ui.components.SettingsToggleItem
import com.fl0w.speye.ui.viewmodel.GoogleDriveViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.fl0w.speye.utils.DriveAuthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fl0w.speye.utils.SpeyeLogger
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoogleDriveScreen(onBack: () -> Unit, viewModel: GoogleDriveViewModel = viewModel()) {
    val account by viewModel.account.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsState()
    val selectedFrequency by viewModel.backupFrequency.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    var expandedFrequency by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val acc = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                viewModel.handleSignInResult(acc)
            } catch (e: Exception) {
                val message = DriveAuthManager.getErrorMessage(context, e)
                SpeyeLogger.e("GoogleDriveScreen", "Sign in failed: $message", e)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    )

    val frequencyLabels = mapOf(
        "Daily" to stringResource(R.string.freq_daily),
        "Weekly" to stringResource(R.string.freq_weekly),
        "Monthly" to stringResource(R.string.freq_monthly),
        "Manual" to stringResource(R.string.freq_manual)
    )
    val frequencies = listOf("Daily", "Weekly", "Monthly", "Manual")

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
                    stringResource(R.string.drive_title),
                    color = SpeyeTheme.colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backup Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isBusy = isBackingUp || isRestoring
            
            // Backup Now
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isBusy || account == null) Color.Gray else SpeyeTheme.colors.primary)
                    .clickable(enabled = !isBusy && account != null) { viewModel.backupNow() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isBackingUp) stringResource(R.string.uploading) else stringResource(R.string.backup_now),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            // Restore from Cloud
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isBusy || account == null) Color.Gray else SpeyeTheme.colors.secondary)
                    .clickable(enabled = !isBusy && account != null) { viewModel.restoreFromCloud() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRestoring) stringResource(R.string.merging) else stringResource(R.string.restore),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Account Info Section
        SettingsSection(title = stringResource(R.string.connected_account)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SpeyeTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountCircle, null, tint = SpeyeTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.email ?: stringResource(R.string.not_signed_in),
                        color = SpeyeTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val storageText = storageInfo?.let { (limit, usage) ->
                        val free = (limit - usage) / (1024 * 1024 * 1024f)
                        val total = limit / (1024 * 1024 * 1024f)
                        stringResource(R.string.storage_info, free, total)
                    } ?: if (account != null) stringResource(R.string.fetching_storage) else stringResource(R.string.sign_in_to_enable)

                    Text(
                        text = storageText,
                        color = SpeyeTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )

                    if (account != null && storageInfo != null) {
                        val (limit, usage) = storageInfo!!
                        val progress = if (limit > 0) usage.toFloat() / limit else 0f
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = SpeyeTheme.colors.primary,
                            trackColor = SpeyeTheme.colors.divider,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        if (account == null) {
                            SpeyeLogger.d("GoogleDriveScreen", "Requesting sign in...")
                            signInLauncher.launch(DriveAuthManager.getSignInIntent(context))
                        } else {
                            viewModel.signOut()
                        }
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (account == null) stringResource(R.string.sign_in_google) else stringResource(R.string.sign_out),
                    color = SpeyeTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Sync Settings Section
        SettingsSection(title = stringResource(R.string.sync_settings)) {
            SettingsToggleItem(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.auto_backup),
                subtitle = stringResource(R.string.auto_backup_sub),
                checked = isAutoBackupEnabled,
                onCheckedChange = { viewModel.setAutoBackupEnabled(it) }
            )
            
            Box {
                SettingsItem(
                    icon = Icons.Default.Timelapse,
                    title = stringResource(R.string.backup_frequency),
                    subtitle = frequencyLabels[selectedFrequency] ?: selectedFrequency,
                    onClick = { expandedFrequency = true }
                )
                com.fl0w.speye.ui.components.SpeyeDropdownMenu(
                    expanded = expandedFrequency,
                    onDismissRequest = { expandedFrequency = false },
                    modifier = Modifier.background(SpeyeTheme.colors.surface)
                ) {
                    frequencies.forEach { freq ->
                        DropdownMenuItem(
                            text = { Text(frequencyLabels[freq] ?: freq, color = if (freq == selectedFrequency) SpeyeTheme.colors.primary else SpeyeTheme.colors.textPrimary) },
                            onClick = { 
                                viewModel.setBackupFrequency(freq)
                                expandedFrequency = false 
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, null, tint = SpeyeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.last_backup), color = SpeyeTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    val neverStr = stringResource(R.string.never)
                    val lastSyncStr = remember(lastBackupTime, neverStr) {
                        if (lastBackupTime > 0) {
                            SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault()).format(Date(lastBackupTime))
                        } else {
                            neverStr
                        }
                    }
                    Text(lastSyncStr, color = SpeyeTheme.colors.textSecondary, fontSize = 12.sp)
                }
            }
        }

        // Danger Zone
        SettingsSection(title = stringResource(R.string.danger_zone)) {
            SettingsItem(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.delete_cloud_saves),
                subtitle = stringResource(R.string.delete_cloud_saves_sub),
                isDanger = true,
                onClick = { showDeleteConfirm = true }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDeleteConfirm) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDeleteConfirm = false }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(SpeyeTheme.colors.surface)
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        stringResource(R.string.delete_cloud_confirm),
                        color = SpeyeTheme.colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.delete_cloud_desc),
                        color = SpeyeTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(stringResource(R.string.cancel), color = SpeyeTheme.colors.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpeyeTheme.colors.error.copy(alpha = 0.2f))
                                .clickable { 
                                    viewModel.deleteCloudSaves()
                                    showDeleteConfirm = false 
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.delete_all), color = SpeyeTheme.colors.error, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
