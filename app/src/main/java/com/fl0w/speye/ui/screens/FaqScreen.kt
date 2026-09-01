package com.fl0w.speye.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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

@Composable
fun FaqScreen(onBack: () -> Unit) {
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
                    stringResource(R.string.faq),
                    color = SpeyeTheme.colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.faq_general)) {
            FaqItem(
                question = stringResource(R.string.faq_q1),
                answer = stringResource(R.string.faq_a1)
            )
            FaqItem(
                question = stringResource(R.string.faq_q2),
                answer = stringResource(R.string.faq_a2)
            )
            FaqItem(
                question = stringResource(R.string.faq_q3),
                answer = stringResource(R.string.faq_a3)
            )
        }

        SettingsSection(title = stringResource(R.string.faq_security)) {
            FaqItem(
                question = stringResource(R.string.faq_q4),
                answer = stringResource(R.string.faq_a4)
            )
            FaqItem(
                question = stringResource(R.string.faq_q5),
                answer = stringResource(R.string.faq_a5)
            )
            FaqItem(
                question = stringResource(R.string.faq_q6),
                answer = stringResource(R.string.faq_a6)
            )
        }

        SettingsSection(title = stringResource(R.string.faq_misc)) {
            FaqItem(
                question = stringResource(R.string.faq_q7),
                answer = stringResource(R.string.faq_a7)
            )
            FaqItem(
                question = stringResource(R.string.faq_q8),
                answer = stringResource(R.string.faq_a8)
            )
            FaqItem(
                question = stringResource(R.string.faq_q9),
                answer = stringResource(R.string.faq_a9)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = question,
                color = SpeyeTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = SpeyeTheme.colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        val reduceMotion = SpeyeTheme.reduceMotion
        if (reduceMotion) {
            if (expanded) {
                Text(
                    text = answer,
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = answer,
                    color = SpeyeTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
