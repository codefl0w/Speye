package com.fl0w.speye.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fl0w.speye.data.db.AppDatabase
import com.fl0w.speye.data.model.IgnoredAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isIgnored: Boolean,
    val isSystem: Boolean
)

enum class AppFilter { ALL, USER, SYSTEM }

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).ignoredAppDao()
    private val pm = application.packageManager

    private val _installedApps = MutableStateFlow<List<ApplicationInfo>>(emptyList())
    private val _currentFilter = MutableStateFlow(AppFilter.USER)
    val currentFilter: StateFlow<AppFilter> = _currentFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadInstalledApps()
    }

    val apps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        dao.getAllIgnoredApps(),
        _currentFilter,
        _searchQuery
    ) { installed, ignored, filter, query ->
        val ignoredSet = ignored.map { it.packageName }.toSet()
        installed.filter { info ->
            val isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !isSystemApp
                AppFilter.SYSTEM -> isSystemApp
            }
        }.map { info ->
            AppInfo(
                packageName = info.packageName,
                appName = pm.getApplicationLabel(info).toString(),
                isIgnored = ignoredSet.contains(info.packageName),
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.filter { app ->
            if (query.isBlank()) true
            else app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
        }.sortedBy { it.appName }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            _installedApps.value = installed

            // Pre-warm icon cache on background thread for smooth scrolling
            val context = getApplication<Application>()
            installed.forEach { info ->
                try {
                    com.fl0w.speye.utils.AppIconCache.getIcon(context, info.packageName)
                } catch (_: Exception) {}
            }
        }
    }

    fun setFilter(filter: AppFilter) {
        _currentFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleIgnore(packageName: String, currentIgnored: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentIgnored) {
                dao.unignoreApp(IgnoredAppEntity(packageName))
            } else {
                dao.ignoreApp(IgnoredAppEntity(packageName))
            }
        }
    }
}
