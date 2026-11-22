package com.cctomato.tomatofile.data.store

import android.content.Context
import com.cctomato.tomatofile.data.model.SortMode
import com.cctomato.tomatofile.data.model.ThemeMode
import com.cctomato.tomatofile.data.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App wide user preferences. Values are kept in SharedPreferences and exposed as StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _cacheLimitMb = MutableStateFlow(prefs.getInt(KEY_CACHE_LIMIT, DEFAULT_CACHE_MB))
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    private val _viewMode = MutableStateFlow(
        if (prefs.getString(KEY_VIEW, null) == ViewMode.GRID.name) ViewMode.GRID else ViewMode.LIST
    )
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.fromName(prefs.getString(KEY_SORT, null)))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setCacheLimitMb(mb: Int) {
        val safe = mb.coerceIn(0, 4096)
        prefs.edit().putInt(KEY_CACHE_LIMIT, safe).apply()
        _cacheLimitMb.value = safe
    }

    fun setViewMode(mode: ViewMode) {
        prefs.edit().putString(KEY_VIEW, mode.name).apply()
        _viewMode.value = mode
    }

    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT, mode.name).apply()
        _sortMode.value = mode
    }

    val cacheLimitBytes: Long
        get() = if (_cacheLimitMb.value <= 0) Long.MAX_VALUE else _cacheLimitMb.value.toLong() * 1024L * 1024L

    private companion object {
        const val PREFS = "tomato_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_CACHE_LIMIT = "cache_limit_mb"
        const val KEY_VIEW = "view_mode"
        const val KEY_SORT = "sort_mode"
        const val DEFAULT_CACHE_MB = 512
    }
}