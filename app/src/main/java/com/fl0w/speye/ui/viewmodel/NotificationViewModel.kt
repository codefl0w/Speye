package com.fl0w.speye.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.model.NotificationWithHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.fl0w.speye.utils.SpeyeLogger
import java.io.File

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).notificationDao()
    private val appSettings = com.fl0w.speye.data.settings.AppSettingsManager(application)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            appSettings.retentionDays.collect {
                com.fl0w.speye.utils.RetentionCleaner.pruneExpired(application)
            }
        }
    }

    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val groupedNotifications: StateFlow<Map<String, List<NotificationWithHistory>>> = dao.getAllNotificationsWithHistory()
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) {
                list.groupBy { it.notification.packageName }
            } else {
                list.filter { item ->
                    item.notification.title?.contains(query, ignoreCase = true) == true ||
                            item.notification.text?.contains(query, ignoreCase = true) == true ||
                            item.notification.appName?.contains(query, ignoreCase = true) == true ||
                            item.notification.packageName.contains(query, ignoreCase = true) ||
                            item.history.any { it.oldText?.contains(query, ignoreCase = true) == true }
                }.groupBy { it.notification.packageName }
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroup(packageName: String) {
        _expandedGroups.update { current ->
            if (current.contains(packageName)) current - packageName
            else current + packageName
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val notif = dao.getNotificationById(id)
            notif?.audioPath?.let { File(it).delete() }
            dao.deleteById(id)
        }
    }

    fun deleteGroup(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val notifs = dao.getNotificationsByPackageName(packageName)
            notifs.forEach { it.audioPath?.let { p -> File(p).delete() } }
            dao.deleteByPackageName(packageName)
        }
    }

    fun ignoreApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(getApplication()).ignoredAppDao().ignoreApp(
                com.fl0w.speye.data.model.IgnoredAppEntity(packageName)
            )
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
            com.fl0w.speye.utils.RetentionCleaner.sweepOrphanImages(getApplication(), gracePeriodMs = 0L)
            com.fl0w.speye.utils.RetentionCleaner.sweepOrphanAudio(getApplication(), gracePeriodMs = 0L)
        }
    }

    fun deleteHistoryItem(historyId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteHistoryById(historyId)
        }
    }
}

