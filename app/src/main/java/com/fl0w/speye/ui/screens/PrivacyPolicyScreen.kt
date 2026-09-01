package com.fl0w.speye.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}
