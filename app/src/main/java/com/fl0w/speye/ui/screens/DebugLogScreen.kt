package com.fl0w.speye.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fl0w.speye.R
import com.fl0w.speye.SpeyeTheme
import com.fl0w.speye.utils.SpeyeLogger

@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val logs by SpeyeLogger.logs.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = SpeyeTheme.colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.view_logs).uppercase(),
                color = SpeyeTheme.colors.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { SpeyeLogger.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = SpeyeTheme.colors.textSecondary)
                }
                IconButton(onClick = {
                    val logText = logs.joinToString("\n")
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Speye Debug Logs")
                        putExtra(Intent.EXTRA_TEXT, logText)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = SpeyeTheme.colors.primary)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SpeyeTheme.colors.divider)
        )

        // Codebox View
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    color = if (log.contains("E/")) SpeyeTheme.colors.error else Color.Green,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
