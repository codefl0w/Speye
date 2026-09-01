package com.fl0w.speye.utils

import android.text.Spanned
import android.text.style.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

object HtmlUtils {
    fun fromHtmlToAnnotatedString(html: String?): AnnotatedString {
        if (html == null) return AnnotatedString("")
        
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        val fullText = spanned.toString()
        val annotated = buildAnnotatedString {
            append(fullText)
            
            spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                if (start in 0..fullText.length && end in start..fullText.length) {
                    when (span) {
                        is StyleSpan -> {
                            when (span.style) {
                                android.graphics.Typeface.BOLD -> {
                                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                                }
                                android.graphics.Typeface.ITALIC -> {
                                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                                }
                                android.graphics.Typeface.BOLD_ITALIC -> {
                                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                                }
                            }
                        }
                        is UnderlineSpan -> {
                            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                        }
                        is StrikethroughSpan -> {
                            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                        }
                        is ForegroundColorSpan -> {
                            addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                        }
                    }
                }
            }
        }
        
        val trimmedText = annotated.text.trim()
        if (trimmedText == annotated.text) return annotated
        val startOffset = annotated.text.indexOf(trimmedText)
        if (startOffset < 0) return annotated
        val endOffset = startOffset + trimmedText.length
        return annotated.subSequence(startOffset, endOffset)
    }
}
