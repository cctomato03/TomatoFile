package com.cctomato.tomatofile.data.repo

import com.cctomato.tomatofile.data.cache.RemoteCache
import com.cctomato.tomatofile.data.fs.FileSystemClient
import com.cctomato.tomatofile.data.fs.FtpFileSystem
import com.cctomato.tomatofile.data.fs.LocalFileSystem
import com.cctomato.tomatofile.data.fs.SmbFileSystem
import com.cctomato.tomatofile.data.fs.WebDavFileSystem
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Entry point used by the UI: creates protocol clients, lists directories, and resolves a remote
 * file to a cached local file for previews.
 */
class FileRepository(private val cache: RemoteCache) {
    private val clients = ConcurrentHashMap<String, FileSystemClient>()

    fun client(source: FileSource): FileSystemClient = clients.getOrPut(source.id) {
        when (source.type) {
            SourceType.LOCAL -> LocalFileSystem(source)
            SourceType.WEBDAV -> WebDavFileSystem(source)
            SourceType.SMB -> SmbFileSystem(source)
            SourceType.FTP -> FtpFileSystem(source)
        }
    }

    suspend fun list(source: FileSource, path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        client(source).list(path)
    }

    suspend fun test(source: FileSource): Int = withContext(Dispatchers.IO) {
        client(source).list("/").size
    }

    /** Downloads (or reuses) a local copy for previews. Local sources are returned in place. */
    suspend fun localFile(source: FileSource, entry: FileEntry): File = withContext(Dispatchers.IO) {
        if (entry.isDirectory) throw IllegalArgumentException("目录不能作为文件预览")
        if (source.type == SourceType.LOCAL) {
            val local = client(source) as LocalFileSystem
            local.fileFor(entry.path)
        } else {
            cache.get(source, entry) { client(source).openStream(entry.path) }
        }
    }

    suspend fun invalidate(sourceId: String) {
        val removed = clients.remove(sourceId)
        if (removed != null) runCatching { removed.close() }
    }

    suspend fun invalidateAll() {
        val snapshot = clients.values.toList()
        clients.clear()
        snapshot.forEach { runCatching { it.close() } }
    }

    suspend fun close() = invalidateAll()

    fun cachedFileCount(): Int = clients.size
}