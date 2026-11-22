package com.cctomato.tomatofile.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.fs.friendlyMessage
import com.cctomato.tomatofile.data.image.ImageMetadataReader
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.model.ImageMetadata
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.ui.nav.Routes
import com.cctomato.tomatofile.util.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Ready(
        val source: FileSource,
        val images: List<FileEntry>,
        val startIndex: Int,
        val parentPath: String
    ) : ViewerUiState

    data class Error(val message: String) : ViewerUiState
}

class ViewerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()
    private val currentPath: String = Routes.decodePath(savedStateHandle.get<String>("path"))
    private val metadataCache = ConcurrentHashMap<String, ImageMetadata>()

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = ViewerUiState.Loading
            val source = container.sources.get(sourceId)
            if (source == null) {
                _state.value = ViewerUiState.Error("存储配置已不存在")
                return@launch
            }
            val parent = FsPaths.parent(currentPath)
            runCatching {
                val siblings = container.repository.list(source, parent)
                val images = siblings
                    .filter { !it.isDirectory && it.isImage }
                    .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
                if (images.isEmpty()) throw IllegalStateException("此目录中没有可查看的图片")
                val index = images.indexOfFirst { it.path == currentPath }.coerceAtLeast(0)
                ViewerUiState.Ready(source, images, index, parent)
            }.onSuccess { ready ->
                _state.value = ready
            }.onFailure { throwable ->
                _state.value = ViewerUiState.Error(
                    throwable.friendlyMessage("无法打开图片")
                )
            }
        }
    }

    suspend fun fileFor(entry: FileEntry): File {
        val state = _state.value as? ViewerUiState.Ready
            ?: throw IllegalStateException("图片列表尚未加载")
        return container.repository.localFile(state.source, entry)
    }

    suspend fun metadataFor(entry: FileEntry): ImageMetadata {
        metadataCache[entry.path]?.let { return it }
        val file = fileFor(entry)
        val metadata = withContext(Dispatchers.IO) {
            ImageMetadataReader.read(file, entry.name)
        }
        metadataCache[entry.path] = metadata
        return metadata
    }
}