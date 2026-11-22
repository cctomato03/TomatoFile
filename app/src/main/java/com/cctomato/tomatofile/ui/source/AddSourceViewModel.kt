package com.cctomato.tomatofile.ui.source

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.fs.friendlyMessage
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.model.SourceType
import com.cctomato.tomatofile.tomatoContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class SourceForm(
    val type: SourceType = SourceType.WEBDAV,
    val name: String = "",
    val localPath: String = "/storage/emulated/0",
    val url: String = "",
    val host: String = "",
    val port: String = "",
    val share: String = "",
    val rootPath: String = "/",
    val domain: String = "",
    val username: String = "",
    val password: String = "",
    val secure: Boolean = false
)

data class TestState(
    val testing: Boolean = false,
    val message: String? = null,
    val success: Boolean = false
)

class AddSourceViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    private val editId: String = savedStateHandle.get<String>("editId").orEmpty()
    private val newId: String = UUID.randomUUID().toString()
    private val existing: FileSource? = editId.takeIf { it.isNotBlank() }?.let { container.sources.get(it) }

    private val _form = MutableStateFlow(
        existing?.let { source ->
            SourceForm(
                type = source.type,
                name = source.name,
                localPath = source.localPath.ifBlank { "/storage/emulated/0" },
                url = source.url,
                host = source.host,
                port = source.port.takeIf { it > 0 }?.toString().orEmpty(),
                share = source.share,
                rootPath = source.rootPath.ifBlank { "/" },
                domain = source.domain,
                username = source.username,
                password = source.password,
                secure = source.secure
            )
        } ?: SourceForm()
    )
    val form: StateFlow<SourceForm> = _form.asStateFlow()

    private val _test = MutableStateFlow(TestState())
    val testState: StateFlow<TestState> = _test.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    val isEditing: Boolean = existing != null
    val isBuiltIn: Boolean = existing?.builtIn == true

    fun update(transform: (SourceForm) -> SourceForm) {
        _form.update(transform)
        if (_test.value.message != null || _test.value.testing) {
            _test.value = TestState()
        }
    }

    fun testConnection() {
        val error = validate()
        if (error != null) {
            _test.value = TestState(message = error, success = false)
            return
        }
        val probe = buildSource().copy(id = "probe-${UUID.randomUUID()}")
        _test.value = TestState(testing = true)
        viewModelScope.launch {
            runCatching { container.repository.test(probe) }
                .onSuccess { count ->
                    _test.value = TestState(
                        testing = false,
                        message = "连接成功，根目录包含 $count 个项目",
                        success = true
                    )
                }
                .onFailure { throwable ->
                    _test.value = TestState(
                        testing = false,
                        message = throwable.friendlyMessage("连接失败"),
                        success = false
                    )
                }
            container.repository.invalidate(probe.id)
        }
    }

    fun save(onSaved: (FileSource) -> Unit) {
        val error = validate()
        if (error != null) {
            _test.value = TestState(message = error, success = false)
            return
        }
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val source = buildSource()
            container.repository.invalidate(source.id)
            if (source.type != SourceType.LOCAL) {
                container.cache.clearSource(source.id)
            }
            val saved = container.sources.save(source)
            _saving.value = false
            onSaved(saved)
        }
    }

    private fun validate(): String? {
        val form = _form.value
        if (form.name.isBlank()) return "请给这个存储起一个名字"
        return when (form.type) {
            SourceType.LOCAL -> {
                val path = form.localPath.trim()
                if (path.isBlank()) "请填写本地目录路径"
                else if (!File(path).isDirectory) "目录不存在或无法访问：$path"
                else null
            }
            SourceType.WEBDAV -> {
                if (form.url.isBlank()) "请填写 WebDAV 地址"
                else null
            }
            SourceType.SMB -> when {
                form.host.isBlank() -> "请填写 SMB 服务器地址"
                form.share.isBlank() -> "请填写共享名，例如 shared"
                else -> portError(form.port)
            }
            SourceType.FTP -> when {
                form.host.isBlank() -> "请填写 FTP 服务器地址"
                else -> portError(form.port)
            }
        }
    }

    private fun portError(port: String): String? {
        if (port.isBlank()) return null
        val value = port.toIntOrNull()
        return if (value == null || value !in 1..65535) {
            "端口必须是 1-65535 之间的数字"
        } else {
            null
        }
    }

    private fun buildSource(): FileSource {
        val form = _form.value
        val id = existing?.id ?: newId
        return FileSource(
            id = id,
            type = form.type,
            name = form.name.trim(),
            localPath = form.localPath.trim(),
            url = form.url.trim(),
            host = form.host.trim(),
            port = form.port.trim().toIntOrNull() ?: 0,
            share = form.share.trim().trim('/'),
            rootPath = FsPaths.normalize(form.rootPath.ifBlank { "/" }),
            domain = form.domain.trim(),
            username = form.username.trim(),
            password = form.password,
            secure = form.secure,
            builtIn = existing?.builtIn == true
        )
    }
}