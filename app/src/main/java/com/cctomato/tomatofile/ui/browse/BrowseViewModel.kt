package com.cctomato.tomatofile.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.fs.friendlyMessage
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.ui.nav.Routes
import com.cctomato.tomatofile.util.StoragePermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val source: FileSource? = null,
    val path: String = "/",
    val entries: List<FileEntry> = emptyList(),
    val error: String? = null,
    val permissionRequired: Boolean = false
)

class BrowseViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()
    private val initialPath: String = Routes.decodePath(savedStateHandle.get<String>("path"))
    private val _state = MutableStateFlow(BrowserState(path = initialPath))
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    private var requestId = 0
    private var scrollRequestVersion = 0
    private var consumedScrollVersion = -1

    private data class DirectorySnapshot(val path: String, val entries: List<FileEntry>)

    // Parent directories already visited. Returning pops a snapshot instead of hitting the
    // network again, so the list and its scroll position survive a back navigation.
    private val history = mutableListOf<DirectorySnapshot>()

    init {
        load(initialPath)
    }

    /** True exactly once per navigation request; lets the screen scroll to top without
     * resetting the position when returning from a preview. */
    fun consumeScrollRequest(): Boolean {
        if (consumedScrollVersion == scrollRequestVersion) return false
        consumedScrollVersion = scrollRequestVersion
        return true
    }

    private fun permissionNeeded(source: FileSource?): Boolean =
        source?.type == com.cctomato.tomatofile.data.model.SourceType.LOCAL &&
            !StoragePermissions.hasAllFilesAccess(getApplication())

    fun load(path: String, refresh: Boolean = false) {
        val normalized = FsPaths.normalize(path)
        val current = ++requestId
        // Clear the previous directory immediately so a slow listing cannot show stale files.
        _state.update {
            it.copy(
                loading = !refresh,
                refreshing = refresh,
                path = normalized,
                entries = if (refresh) it.entries else emptyList(),
                error = null
            )
        }
        viewModelScope.launch {
            val source = container.sources.get(sourceId)
            if (source == null) {
                _state.value = BrowserState(
                    loading = false,
                    path = normalized,
                    error = "这个存储配置已被删除"
                )
                return@launch
            }
            _state.update {
                it.copy(
                    loading = !refresh,
                    refreshing = refresh,
                    path = normalized,
                    entries = if (refresh) it.entries else emptyList(),
                    source = source,
                    error = null,
                    permissionRequired = permissionNeeded(source)
                )
            }
            runCatching {
                container.repository.list(source, normalized)
            }.onSuccess { entries ->
                if (current != requestId) return@onSuccess
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        entries = entries,
                        error = null,
                        permissionRequired = permissionNeeded(source)
                    )
                }
            }.onFailure { throwable ->
                if (current != requestId) return@onFailure
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        entries = emptyList(),
                        error = throwable.friendlyMessage("无法读取目录"),
                        permissionRequired = permissionNeeded(source)
                    )
                }
            }
        }
    }

    fun refresh() = load(_state.value.path, refresh = true)

    fun navigateTo(path: String) {
        val normalized = FsPaths.normalize(path)
        val current = _state.value
        if (normalized == current.path) return

        // Breadcrumb / back navigation to an ancestor: restore the cached listing.
        val cachedIndex = history.indexOfLast { it.path == normalized }
        if (cachedIndex >= 0) {
            val snapshot = history[cachedIndex]
            while (history.size > cachedIndex) history.removeLast()
            restore(snapshot)
            return
        }

        // Descending into a child: remember the current directory first.
        if (!current.loading && current.error == null) {
            history.addLast(DirectorySnapshot(current.path, current.entries))
            if (history.size > 32) history.removeAt(0)
        }
        scrollRequestVersion++
        load(normalized)
    }

    fun open(path: String) = navigateTo(path)

    fun goUp() {
        val path = _state.value.path
        if (!FsPaths.isRoot(path)) navigateTo(FsPaths.parent(path))
    }

    private fun restore(snapshot: DirectorySnapshot) {
        requestId++
        val current = _state.value
        _state.value = BrowserState(
            loading = false,
            refreshing = false,
            source = current.source,
            path = snapshot.path,
            entries = snapshot.entries,
            error = null,
            permissionRequired = permissionNeeded(current.source)
        )
    }

    fun retry() = load(_state.value.path)

    fun canGoUp(): Boolean = !FsPaths.isRoot(_state.value.path)
}