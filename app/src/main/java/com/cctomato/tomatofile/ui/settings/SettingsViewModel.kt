package com.cctomato.tomatofile.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cctomato.tomatofile.data.model.ThemeMode
import com.cctomato.tomatofile.tomatoContainer
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = application.tomatoContainer.settings
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
    val cacheLimitMb: StateFlow<Int> = settings.cacheLimitMb
    val forceTvLayout: StateFlow<Boolean> = settings.forceTvLayout

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setCacheLimit(mb: Int) = settings.setCacheLimitMb(mb)
    fun setForceTvLayout(enabled: Boolean) = settings.setForceTvLayout(enabled)
}