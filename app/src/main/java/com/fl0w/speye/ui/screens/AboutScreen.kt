package com.fl0w.speye.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fl0w.speye.BuildConfig
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.ui.components.SettingsItem
import com.fl0w.speye.ui.components.SettingsSection

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = SpeyeTheme.colors.textPrimary
                )
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
                    stringResource(R.string.about_speye),
                    color = SpeyeTheme.colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero Description Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SpeyeTheme.colors.surface)
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    color = SpeyeTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_app_desc),
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Device & Build Specs
        SettingsSection(title = stringResource(R.string.device_and_build)) {
            val supportedAbis = remember { Build.SUPPORTED_ABIS.joinToString(", ") }
            val deviceModel = remember {
                val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                "$manufacturer ${Build.MODEL}"
            }
            val androidVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.build_flavor),
                subtitle = BuildConfig.FLAVOR.uppercase()
            )
            SettingsItem(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.device_architecture),
                subtitle = supportedAbis
            )
            SettingsItem(
                icon = Icons.Default.PhoneAndroid,
                title = stringResource(R.string.device_model),
                subtitle = deviceModel
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = stringResource(R.string.android_version),
                subtitle = androidVersion
            )
        }

        // Project Info & Links
        SettingsSection(title = stringResource(R.string.project_info)) {
            SettingsItem(
                icon = Icons.Default.Person,
                title = stringResource(R.string.creator),
                subtitle = "fl0w"
            )
            SettingsItem(
                icon = Icons.Default.Source,
                title = stringResource(R.string.source_code),
                subtitle = "github.com/codefl0w/Speye",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codefl0w/Speye"))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
