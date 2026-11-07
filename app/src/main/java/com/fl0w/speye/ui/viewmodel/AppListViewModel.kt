package com.fl0w.speye.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GranularFilterType {
    TEXT,
    LAZY_ASSETS,
    STICKERS,
    MEDIA,
    VOICE,
    PROGRESS,
    ACTIONS
}

@Immutable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isIgnored: Boolean,
    val isSystem: Boolean,
    val allowText: Boolean = true,
    val allowLazyAssets: Boolean = true,
    val allowStickers: Boolean = true,
    val allowMedia: Boolean = true,
    val allowVoice: Boolean = true,
    val allowProgress: Boolean = true,
    val allowActions: Boolean = true
) {
    val isMainToggleOn: Boolean
        get() = allowText || allowLazyAssets || allowStickers || allowMedia || allowVoice || allowProgress || allowActions

    val isAllOn: Boolean
        get() = allowText && allowLazyAssets && allowStickers && allowMedia && allowVoice && allowProgress && allowActions
}

enum class AppFilter { ALL, USER, SYSTEM }

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).ignoredAppDao()
    private val pm = application.packageManager

    private data class RawAppEntry(
        val packageName: String,
        val appName: String,
        val isSystem: Boolean
    )

    private val _installedApps = MutableStateFlow<List<RawAppEntry>>(emptyList())
    private val _currentFilter = MutableStateFlow(AppFilter.USER)
    val currentFilter: StateFlow<AppFilter> = _currentFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _expandedApps = MutableStateFlow<Set<String>>(emptySet())
    val expandedApps: StateFlow<Set<String>> = _expandedApps

    init {
        loadInstalledApps()
    }

    val apps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        dao.getAllIgnoredApps(),
        _currentFilter,
        _searchQuery
    ) { installed, ignored, filter, query ->
        val filterMap = ignored.associateBy { it.packageName }
        installed.asSequence()
            .filter { entry ->
                when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !entry.isSystem
                    AppFilter.SYSTEM -> entry.isSystem
                }
            }
            .filter { entry ->
                if (query.isBlank()) true
                else entry.appName.contains(query, ignoreCase = true) || entry.packageName.contains(query, ignoreCase = true)
            }
            .map { entry ->
                val cfg = filterMap[entry.packageName]
                if (cfg == null) {
                    AppInfo(
                        packageName = entry.packageName,
                        appName = entry.appName,
                        isIgnored = false,
                        isSystem = entry.isSystem
                    )
                } else {
                    AppInfo(
                        packageName = entry.packageName,
                        appName = entry.appName,
                        isIgnored = cfg.isAllOff,
                        isSystem = entry.isSystem,
                        allowText = cfg.allowText,
                        allowLazyAssets = cfg.allowLazyAssets,
                        allowStickers = cfg.allowStickers,
                        allowMedia = cfg.allowMedia,
                        allowVoice = cfg.allowVoice,
                        allowProgress = cfg.allowProgress,
                        allowActions = cfg.allowActions
                    )
                }
            }
            .toList()
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
            val resolved = installed.map { info ->
                RawAppEntry(
                    packageName = info.packageName,
                    appName = pm.getApplicationLabel(info).toString(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.appName.lowercase() }
            _installedApps.value = resolved

            // Pre-warm icon cache for user apps first (capped to cache capacity 80)
            val context = getApplication<Application>()
            resolved.asSequence()
                .filter { !it.isSystem }
                .take(80)
                .forEach { entry ->
                    try {
                        com.fl0w.speye.utils.AppIconCache.getIcon(context, entry.packageName)
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

    fun toggleExpand(packageName: String) {
        _expandedApps.update { current ->
            if (current.contains(packageName)) current - packageName
            else current + packageName
        }
    }

    fun toggleIgnore(packageName: String, currentIgnored: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentIgnored) {
                dao.deleteByPackageName(packageName)
            } else {
                dao.ignoreApp(IgnoredAppEntity.allOff(packageName))
            }
        }
    }

    fun setGranularFilter(packageName: String, type: GranularFilterType, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.getAppFilter(packageName) ?: IgnoredAppEntity.allOn(packageName)
            val updated = when (type) {
                GranularFilterType.TEXT -> current.copy(allowText = enabled)
                GranularFilterType.LAZY_ASSETS -> current.copy(allowLazyAssets = enabled)
                GranularFilterType.STICKERS -> current.copy(allowStickers = enabled)
                GranularFilterType.MEDIA -> current.copy(allowMedia = enabled)
                GranularFilterType.VOICE -> current.copy(allowVoice = enabled)
                GranularFilterType.PROGRESS -> current.copy(allowProgress = enabled)
                GranularFilterType.ACTIONS -> current.copy(allowActions = enabled)
            }
            if (updated.isAllOn) {
                dao.deleteByPackageName(packageName)
            } else {
                dao.ignoreApp(updated)
            }
        }
    }
}
