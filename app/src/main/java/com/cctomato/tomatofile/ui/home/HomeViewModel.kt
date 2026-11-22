package com.cctomato.tomatofile.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.model.CacheStats
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.tomatoContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    val sources: StateFlow<List<FileSource>> = container.sources.sources

    private val _stats = MutableStateFlow(CacheStats())
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _stats.value = runCatching { container.cache.stats() }.getOrDefault(CacheStats())
        }
    }

    fun deleteSource(source: FileSource, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val removed = container.sources.delete(source.id)
            if (removed) {
                container.repository.invalidate(source.id)
                container.cache.clearSource(source.id)
                refreshStats()
            }
            onDone(removed)
        }
    }

    fun clearSourceCache(source: FileSource) {
        viewModelScope.launch {
            container.cache.clearSource(source.id)
            refreshStats()
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            container.cache.clearAll()
            refreshStats()
        }
    }
}