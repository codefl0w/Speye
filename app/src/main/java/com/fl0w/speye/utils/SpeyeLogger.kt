package com.fl0w.speye.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SpeyeLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var isLoggingEnabled = false
    private val dateFormatThreadLocal = ThreadLocal.withInitial { 
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) 
    }

    fun setLoggingEnabled(enabled: Boolean) {
        isLoggingEnabled = enabled
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        if (isLoggingEnabled) {
            appendLog("D", tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        if (isLoggingEnabled) {
            val fullMessage = if (throwable != null) {
                "$message\n${Log.getStackTraceString(throwable)}"
            } else {
                message
            }
            appendLog("E", tag, fullMessage)
        }
    }

    private fun appendLog(level: String, tag: String, message: String) {
        val timestamp = dateFormatThreadLocal.get()?.format(Date()) ?: ""
        val logEntry = "[$timestamp] $level/$tag: $message"
        _logs.update { current ->
            (current + logEntry).takeLast(1000) // Keep last 1000 logs
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
