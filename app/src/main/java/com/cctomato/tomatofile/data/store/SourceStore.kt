package com.cctomato.tomatofile.data.store

import android.content.Context
import android.os.Environment
import com.cctomato.tomatofile.data.crypto.TokenCrypto
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.SourceType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** Persists configured file systems and keeps their credentials encrypted at rest. */
class SourceStore(private val context: Context, private val crypto: TokenCrypto) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _sources = MutableStateFlow<List<FileSource>>(emptyList())
    val sources: StateFlow<List<FileSource>> = _sources.asStateFlow()

    init {
        val loaded = load()
        if (loaded.isEmpty()) {
            val seeded = seedLocal()
            _sources.value = listOf(seeded)
            persist(listOf(seeded))
        } else {
            _sources.value = loaded
        }
    }

    private fun load(): List<FileSource> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<FileSource>>() {}.type
            val decoded: List<FileSource> = gson.fromJson(json, type)
            decoded.map { it.copy(password = crypto.decrypt(it.password)) }
                .filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<FileSource>) {
        val encoded = list.map { it.copy(password = crypto.encrypt(it.password)) }
        prefs.edit().putString(KEY_SOURCES, gson.toJson(encoded)).apply()
    }

    private fun seedLocal(): FileSource {
        val external = runCatching { Environment.getExternalStorageDirectory().absolutePath }
            .getOrNull()
        val root = external?.takeIf { it.isNotBlank() } ?: context.filesDir.absolutePath
        return FileSource(
            id = "local-default",
            type = SourceType.LOCAL,
            name = "本机存储",
            localPath = root,
            builtIn = true
        )
    }

    fun get(id: String): FileSource? = _sources.value.firstOrNull { it.id == id }

    fun save(source: FileSource): FileSource {
        val withId = if (source.id.isBlank()) source.copy(id = UUID.randomUUID().toString()) else source
        synchronized(lock) {
            val current = _sources.value.toMutableList()
            val index = current.indexOfFirst { it.id == withId.id }
            if (index >= 0) current[index] = withId else current.add(withId)
            _sources.value = current
            persist(current)
        }
        return withId
    }

    fun delete(id: String): Boolean {
        synchronized(lock) {
            val target = _sources.value.firstOrNull { it.id == id } ?: return false
            if (target.builtIn) return false
            val current = _sources.value.filterNot { it.id == id }
            _sources.value = current
            persist(current)
            return true
        }
    }

    fun addLocalRoot(root: File): FileSource = save(
        FileSource(
            type = SourceType.LOCAL,
            name = root.name.ifBlank { "本地目录" },
            localPath = root.absolutePath
        )
    )

    private companion object {
        const val PREFS = "tomato_sources"
        const val KEY_SOURCES = "sources_json"
    }
}