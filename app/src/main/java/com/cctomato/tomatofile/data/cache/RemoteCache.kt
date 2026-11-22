package com.cctomato.tomatofile.data.cache

import android.content.Context
import com.cctomato.tomatofile.data.model.CacheStats
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.store.SettingsStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk cache for resources downloaded from remote file systems. Every remote file is keyed by
 * source id + path and validated against the remote modification time / size before reuse.
 * Downloads are de-duplicated per key, and an LRU eviction keeps the cache below the user limit.
 */
class RemoteCache(
    context: Context,
    private val settings: SettingsStore
) {
    private val gson = Gson()
    private val rootDir = File(context.cacheDir, "remote-files").apply { mkdirs() }
    private val indexFile = File(rootDir, INDEX_FILE)
    private val indexLock = Mutex()
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    private val entries = LinkedHashMap<String, Entry>()
    private var loaded = false

    data class Entry(
        val key: String,
        val sourceId: String,
        val path: String,
        val fileName: String,
        val size: Long,
        val lastUsed: Long,
        val remoteModified: Long,
        val remoteSize: Long
    )

    /**
     * Returns a local file for [entry], downloading it through [opener] when it is missing or when
     * the remote file changed. Concurrent requests for the same path share one download.
     */
    suspend fun get(
        source: FileSource,
        entry: FileEntry,
        opener: suspend () -> InputStream
    ): File = withContext(Dispatchers.IO) {
        ensureLoaded()
        val key = cacheKey(source.id, entry.path)
        val lock = downloadLocks.getOrPut(key) { Mutex() }
        lock.withLock {
            val cached = indexLock.withLock { entries[key]?.copy() }
            val cachedFile = cached?.let { File(rootDir, it.fileName) }
            if (cached != null && cachedFile != null && cachedFile.isFile && isFresh(cached, entry)) {
                indexLock.withLock {
                    entries[key]?.let { current ->
                        entries[key] = current.copy(lastUsed = System.currentTimeMillis())
                    }
                }
                return@withLock cachedFile
            }

            val fileName = sha256(key)
            val destination = File(rootDir, fileName)
            val temp = File(rootDir, "$fileName.tmp")
            try {
                temp.delete()
                opener().use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
                if (!temp.renameTo(destination)) {
                    temp.copyTo(destination, overwrite = true)
                    temp.delete()
                }
                val downloaded = Entry(
                    key = key,
                    sourceId = source.id,
                    path = entry.path,
                    fileName = fileName,
                    size = destination.length(),
                    lastUsed = System.currentTimeMillis(),
                    remoteModified = entry.lastModified,
                    remoteSize = entry.size
                )
                indexLock.withLock {
                    entries[key] = downloaded
                    persistIndexLocked()
                }
                enforceLimit()
                destination
            } catch (t: Throwable) {
                temp.delete()
                throw t
            }
        }
    }

    private fun isFresh(cached: Entry, entry: FileEntry): Boolean {
        if (cached.remoteModified != entry.lastModified) return false
        if (entry.size > 0 && cached.remoteSize != entry.size) return false
        return true
    }

    suspend fun stats(): CacheStats = withContext(Dispatchers.IO) {
        ensureLoaded()
        indexLock.withLock {
            var total = 0L
            val perSource = HashMap<String, Long>()
            var count = 0
            for (entry in entries.values) {
                val file = File(rootDir, entry.fileName)
                if (!file.isFile) continue
                val length = file.length()
                total += length
                count++
                perSource[entry.sourceId] = (perSource[entry.sourceId] ?: 0L) + length
            }
            CacheStats(
                totalBytes = total,
                maxBytes = settings.cacheLimitBytes,
                itemCount = count,
                perSource = perSource
            )
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        ensureLoaded()
        indexLock.withLock {
            rootDir.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
            entries.clear()
            loaded = true
        }
    }

    suspend fun clearSource(sourceId: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        indexLock.withLock {
            val iterator = entries.values.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.sourceId == sourceId) {
                    File(rootDir, entry.fileName).delete()
                    iterator.remove()
                }
            }
            persistIndexLocked()
        }
    }

    /** Applies the configured size limit. Called after downloads and after settings changes. */
    suspend fun trimToLimit() = withContext(Dispatchers.IO) {
        ensureLoaded()
        enforceLimit()
    }

    private suspend fun enforceLimit() {
        indexLock.withLock {
            val limit = settings.cacheLimitBytes
            if (limit == Long.MAX_VALUE) return@withLock
            var total = entries.values.sumOf { it.size }
            if (total <= limit) return@withLock
            val ordered = entries.values.sortedBy { it.lastUsed }
            for (entry in ordered) {
                if (total <= limit) break
                val file = File(rootDir, entry.fileName)
                val length = if (file.exists()) file.length() else entry.size
                if (file.delete() || !file.exists()) {
                    entries.remove(entry.key)
                    total -= length
                }
            }
            persistIndexLocked()
        }
    }

    private suspend fun ensureLoaded() {
        indexLock.withLock {
            if (loaded) return@withLock
            loaded = true
            if (indexFile.isFile) {
                runCatching {
                    val type = object : TypeToken<List<Entry>>() {}.type
                    val stored: List<Entry> = gson.fromJson(indexFile.readText(), type)
                    stored.forEach { entries[it.key] = it }
                }
            }
            // Remove records whose file disappeared and orphaned temp files from a crash.
            entries.values.removeIf { !File(rootDir, it.fileName).isFile }
            val known = entries.values.mapTo(HashSet()) { it.fileName }
            rootDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name != INDEX_FILE && file.name !in known) {
                    file.delete()
                }
            }
        }
    }

    private fun persistIndexLocked() {
        runCatching {
            val temp = File(rootDir, "$INDEX_FILE.tmp")
            temp.writeText(gson.toJson(entries.values.toList()))
            if (!temp.renameTo(indexFile)) {
                temp.copyTo(indexFile, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun cacheKey(sourceId: String, path: String): String = "$sourceId|$path"

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val INDEX_FILE = "cache-index.json"
    }
}