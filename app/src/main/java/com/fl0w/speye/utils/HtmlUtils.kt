package com.fl0w.speye.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

object HtmlUtils {
    fun fromHtmlToAnnotatedString(html: String?): AnnotatedString {
        if (html.isNullOrEmpty()) return AnnotatedString("")
        
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
                        is BackgroundColorSpan -> {
                            addStyle(SpanStyle(background = Color(span.backgroundColor)), start, end)
                        }
                        is TypefaceSpan -> {
                            if (span.family?.contains("monospace", ignoreCase = true) == true) {
                                addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
                            }
                        }
                        is URLSpan -> {
                            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                        }
                    }
                }
            }
        }
        
        val text = annotated.text
        val startOffset = text.indexOfFirst { !it.isWhitespace() }
        if (startOffset < 0) return AnnotatedString("")
        val endOffset = text.indexOfLast { !it.isWhitespace() } + 1
        if (startOffset == 0 && endOffset == text.length) return annotated
        return annotated.subSequence(startOffset, endOffset)
    }

    fun copyRichText(context: Context, label: String, html: String?) {
        if (html.isNullOrBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val plainText = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        val clip = ClipData.newHtmlText(label, plainText, html)
        clipboard.setPrimaryClip(clip)
    }
}
