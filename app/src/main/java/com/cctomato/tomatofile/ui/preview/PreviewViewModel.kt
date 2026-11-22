package com.cctomato.tomatofile.ui.preview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.fs.friendlyMessage
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.model.PreviewKind
import com.cctomato.tomatofile.data.preview.OfficeTextExtractor
import com.cctomato.tomatofile.data.preview.PdfPageRenderer
import com.cctomato.tomatofile.data.preview.TextContent
import com.cctomato.tomatofile.data.preview.TextFileReader
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PreviewContent {
    data class TextContentUi(val content: TextContent, val file: File) : PreviewContent
    data class PdfFile(val file: File, val pageCount: Int) : PreviewContent
    data class OfficeText(val text: String, val kind: PreviewKind, val file: File) : PreviewContent
    data class External(val file: File, val mimeType: String, val message: String) : PreviewContent
}

data class PreviewState(
    val loading: Boolean = true,
    val sourceName: String = "",
    val entry: FileEntry? = null,
    val content: PreviewContent? = null,
    val error: String? = null
)

class PreviewViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()
    private val path: String = Routes.decodePath(savedStateHandle.get<String>("path"))
    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()
    private var requestId = 0

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        val current = ++requestId
        viewModelScope.launch {
            val source = container.sources.get(sourceId)
            if (source == null) {
                _state.value = PreviewState(loading = false, error = "存储配置已不存在")
                return@launch
            }
            _state.value = PreviewState(loading = true, sourceName = source.name)
            val name = FsPaths.name(path)
            val entry = runCatching {
                val parent = FsPaths.parent(path)
                container.repository.list(source, parent).firstOrNull { it.path == path }
            }.getOrNull() ?: FileEntry(
                name = name,
                path = path,
                isDirectory = false,
                mimeType = FileKinds.mimeForName(name)
            )
            if (entry.isDirectory) {
                _state.value = PreviewState(
                    loading = false,
                    sourceName = source.name,
                    entry = entry,
                    error = "这是一个目录，请在文件浏览器中打开"
                )
                return@launch
            }

            when (entry.kind) {
                PreviewKind.IMAGE -> {
                    _state.value = PreviewState(
                        loading = false,
                        sourceName = source.name,
                        entry = entry,
                        error = "图片请使用全屏图片查看器打开"
                    )
                }
                PreviewKind.TEXT -> loadText(current, source.name, source, entry)
                PreviewKind.PDF -> loadPdf(current, source.name, source, entry)
                PreviewKind.DOCX, PreviewKind.XLSX, PreviewKind.PPTX ->
                    loadOffice(current, source.name, source, entry)
                else -> loadExternal(current, source.name, source, entry)
            }
        }
    }

    private suspend fun loadText(
        current: Int,
        sourceName: String,
        source: com.cctomato.tomatofile.data.model.FileSource,
        entry: FileEntry
    ) {
        runCatching {
            val file = container.repository.localFile(source, entry)
            val content = withContext(Dispatchers.IO) { TextFileReader.read(file) }
            PreviewContent.TextContentUi(content, file)
        }.onSuccess { content ->
            if (current != requestId) return@onSuccess
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                content = content
            )
        }.onFailure { throwable ->
            if (current != requestId) return@onFailure
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                error = throwable.friendlyMessage("无法读取文本文件")
            )
        }
    }

    private suspend fun loadPdf(
        current: Int,
        sourceName: String,
        source: com.cctomato.tomatofile.data.model.FileSource,
        entry: FileEntry
    ) {
        runCatching {
            val file = container.repository.localFile(source, entry)
            val pages = withContext(Dispatchers.IO) {
                PdfPageRenderer(file).use { it.pageCount }
            }
            PreviewContent.PdfFile(file, pages)
        }.onSuccess { content ->
            if (current != requestId) return@onSuccess
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                content = content
            )
        }.onFailure { throwable ->
            if (current != requestId) return@onFailure
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                error = "PDF 打开失败：${throwable.friendlyMessage("文件可能已加密或损坏")}"
            )
        }
    }

    private suspend fun loadOffice(
        current: Int,
        sourceName: String,
        source: com.cctomato.tomatofile.data.model.FileSource,
        entry: FileEntry
    ) {
        runCatching {
            val file = container.repository.localFile(source, entry)
            val text = withContext(Dispatchers.IO) { OfficeTextExtractor.extract(file, entry.kind) }
            PreviewContent.OfficeText(text, entry.kind, file)
        }.onSuccess { content ->
            if (current != requestId) return@onSuccess
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                content = content
            )
        }.onFailure { throwable ->
            if (current != requestId) return@onFailure
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                error = "Office 文档解析失败：${throwable.friendlyMessage("可以尝试用其他应用打开")}"
            )
        }
    }

    private suspend fun loadExternal(
        current: Int,
        sourceName: String,
        source: com.cctomato.tomatofile.data.model.FileSource,
        entry: FileEntry
    ) {
        val message = when (entry.kind) {
            PreviewKind.AUDIO -> "音频文件已准备好，可以交给系统播放器打开"
            PreviewKind.VIDEO -> "视频文件已准备好，可以交给系统播放器打开"
            PreviewKind.ARCHIVE -> "压缩包暂不支持内置解压，可以分享或用其他应用打开"
            PreviewKind.OTHER -> "此格式暂不支持应用内预览，可以交给其他应用打开"
            else -> "可以交给其他应用打开"
        }
        runCatching {
            val file = container.repository.localFile(source, entry)
            PreviewContent.External(file, entry.mimeType, message)
        }.onSuccess { content ->
            if (current != requestId) return@onSuccess
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                content = content
            )
        }.onFailure { throwable ->
            if (current != requestId) return@onFailure
            _state.value = PreviewState(
                loading = false,
                sourceName = sourceName,
                entry = entry,
                error = throwable.friendlyMessage("无法读取文件")
            )
        }
    }
}