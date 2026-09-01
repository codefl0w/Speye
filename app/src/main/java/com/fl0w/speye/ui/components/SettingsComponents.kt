package com.fl0w.speye.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.fl0w.speye.SpeyeTheme

@Composable
fun SpeyeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = SpeyeTheme.reduceMotion
    if (reduceMotion) {
        // Zero-animation instant snap toggle
        Box(
            modifier = modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (checked) SpeyeTheme.colors.primary.copy(alpha = 0.4f)
                    else Color(0xFF333333)
                )
                .clickable { onCheckedChange(!checked) }
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) SpeyeTheme.colors.primary else Color(0xFF888888))
            )
        }
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SpeyeTheme.colors.primary,
                checkedTrackColor = SpeyeTheme.colors.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun SpeyeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduceMotion = SpeyeTheme.reduceMotion
    if (reduceMotion) {
        if (expanded) {
            Popup(
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpeyeTheme.colors.surface)
                        .border(1.dp, SpeyeTheme.colors.divider, RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Column(content = content)
                }
            }
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = title,
            color = SpeyeTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SpeyeTheme.colors.surface)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isPlaceholder: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isPlaceholder) onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpeyeTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (isDanger) SpeyeTheme.colors.error else SpeyeTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDanger) SpeyeTheme.colors.error else SpeyeTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = if (isPlaceholder) SpeyeTheme.colors.primary.copy(alpha = 0.5f) else SpeyeTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = SpeyeTheme.colors.divider)
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpeyeTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SpeyeTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SpeyeTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SpeyeTheme.colors.textSecondary, fontSize = 12.sp)
        }
        SpeyeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
