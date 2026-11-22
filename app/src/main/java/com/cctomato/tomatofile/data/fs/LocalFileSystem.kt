package com.cctomato.tomatofile.data.fs

import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Browsing for the on-device file system rooted at the configured directory. */
class LocalFileSystem(private val source: FileSource) : FileSystemClient {
    private val root: File = File(source.localPath.ifBlank { "/" }).canonicalFile

    fun fileFor(path: String): File = resolve(path)

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val directory = resolve(path)
        if (!directory.exists()) throw FileNotFoundException("目录不存在：${FsPaths.normalize(path)}")
        if (!directory.isDirectory) throw IOException("这不是一个目录")
        val children = directory.listFiles()
            ?: throw IOException("无法读取目录，请授予「所有文件访问权限」")
        children.mapNotNull { child ->
            runCatching { toEntry(path, child) }.getOrNull()
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        val file = resolve(path)
        if (!file.isFile) throw FileNotFoundException("文件不存在")
        FileInputStream(file)
    }

    override suspend fun close() = Unit

    private fun resolve(path: String): File {
        val relative = FsPaths.normalize(path).trimStart('/')
        val target = File(root, relative).canonicalFile
        val rootPrefix = if (root.path.endsWith(File.separator)) root.path else root.path + File.separator
        if (target != root && !target.path.startsWith(rootPrefix)) {
            throw SecurityException("路径超出了此存储的根目录")
        }
        return target
    }

    private fun toEntry(parent: String, file: File): FileEntry {
        val isDirectory = file.isDirectory
        return FileEntry(
            name = file.name,
            path = FsPaths.join(parent, file.name),
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else file.length(),
            lastModified = file.lastModified(),
            mimeType = if (isDirectory) "inode/directory" else FileKinds.mimeForName(file.name)
        )
    }
}