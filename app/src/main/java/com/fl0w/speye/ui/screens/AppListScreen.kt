package com.fl0w.speye.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons
import com.fl0w.speye.ui.viewmodel.AppFilter
import com.fl0w.speye.ui.viewmodel.AppListViewModel

@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val apps by viewModel.apps.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp),
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
                stringResource(R.string.app_list_description),
                color = SpeyeTheme.colors.textSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Tags
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
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
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SpeyeTheme.colors.divider)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                AppItem(
                    app = app,
                    onToggle = { viewModel.toggleIgnore(app.packageName, app.isIgnored) }
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
fun AppItem(app: com.fl0w.speye.ui.viewmodel.AppInfo, onToggle: () -> Unit) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        com.fl0w.speye.utils.AppIconCache.getIcon(context, app.packageName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
            Spacer(modifier = Modifier.width(16.dp))
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
        }

        com.fl0w.speye.ui.components.SpeyeSwitch(
            checked = !app.isIgnored,
            onCheckedChange = { onToggle() }
        )
    }
}
