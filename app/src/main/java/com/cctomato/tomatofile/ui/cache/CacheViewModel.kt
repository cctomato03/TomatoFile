package com.cctomato.tomatofile.ui.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.model.CacheStats
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.tomatoContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CacheViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    val sources: StateFlow<List<FileSource>> = container.sources.sources
    val cacheLimitMb: StateFlow<Int> = container.settings.cacheLimitMb

    private val _stats = MutableStateFlow(CacheStats())
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = runCatching { container.cache.stats() }.getOrDefault(CacheStats())
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            _busy.value = true
            container.cache.clearAll()
            runCatching {
                withContext(Dispatchers.IO) {
                    coil.Coil.imageLoader(getApplication()).diskCache?.clear()
                }
            }
            _stats.value = runCatching { container.cache.stats() }.getOrDefault(CacheStats())
            _busy.value = false
        }
    }

    fun clearSource(sourceId: String) {
        viewModelScope.launch {
            _busy.value = true
            container.cache.clearSource(sourceId)
            _stats.value = runCatching { container.cache.stats() }.getOrDefault(CacheStats())
            _busy.value = false
        }
    }

    fun setCacheLimit(mb: Int) {
        container.settings.setCacheLimitMb(mb)
        viewModelScope.launch {
            container.cache.trimToLimit()
            _stats.value = runCatching { container.cache.stats() }.getOrDefault(CacheStats())
        }
    }
}