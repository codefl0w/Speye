package com.fl0w.speye.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.ui.SpeyeIcons

@Composable
fun WelcomeScreen(
    hasPostNotification: Boolean,
    hasNotificationAccess: Boolean,
    hasUnrestrictedBackground: Boolean,
    onGrantPostNotification: () -> Unit,
    onGrantBackgroundUsage: () -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeyeTheme.colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            SpeyeIcons.Logo,
            contentDescription = null,
            modifier = Modifier.size(width = 100.dp, height = 60.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            "SPEYE",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.welcome_grant_permissions),
            color = SpeyeTheme.colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        PermissionCard(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.perm_post_notif),
            description = stringResource(R.string.perm_post_notif_desc),
            isGranted = hasPostNotification,
            onClick = onGrantPostNotification
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            icon = Icons.Default.Security,
            title = stringResource(R.string.perm_notif_access),
            description = stringResource(R.string.perm_notif_access_desc),
            isGranted = hasNotificationAccess,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            icon = Icons.Default.Bolt,
            title = stringResource(R.string.perm_background),
            description = stringResource(R.string.perm_background_desc),
            isGranted = hasUnrestrictedBackground,
            isOptional = true,
            onClick = onGrantBackgroundUsage
        )

        Spacer(modifier = Modifier.height(32.dp))

        val canStart = hasPostNotification && hasNotificationAccess
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (canStart) SpeyeTheme.colors.primary else Color.Gray)
                .clickable(enabled = canStart) { onStart() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.start_app),
                color = if (canStart) Color.Black else Color.DarkGray,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SpeyeTheme.colors.surface)
            .clickable(enabled = !isGranted) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = if (isOptional && !isGranted) 12.dp else 0.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpeyeTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isGranted) SpeyeTheme.colors.primary else SpeyeTheme.colors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SpeyeTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                if (isGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.granted),
                        tint = SpeyeTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpeyeTheme.colors.primary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            stringResource(R.string.grant),
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        if (isOptional && !isGranted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(bottomEnd = 12.dp))
                    .background(SpeyeTheme.colors.primary.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.optional),
                    color = SpeyeTheme.colors.primary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
