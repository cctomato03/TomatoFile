package com.cctomato.tomatofile.data.fs

import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB2/3 browsing through SMBJ. One authenticated connection is reused per configured source;
 * all operations are serialized with a mutex because SMB shares are not guaranteed to tolerate
 * concurrent create/enumerate calls on every server.
 */
class SmbFileSystem(private val source: FileSource) : FileSystemClient {
    private val mutex = Mutex()
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    private val shareRoot: String = FsPaths.normalize(
        source.rootPath.ifBlank { "/" }
    ).trim('/')

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val share = ensureShare()
            val smbPath = toSmbPath(path)
            try {
                share.list(smbPath)
                    .asSequence()
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map { info ->
                        val isDirectory = (info.fileAttributes and
                            FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                        val mime = if (isDirectory) "inode/directory"
                        else FileKinds.mimeForName(info.fileName)
                        FileEntry(
                            name = info.fileName,
                            path = FsPaths.join(path, info.fileName),
                            isDirectory = isDirectory,
                            size = if (isDirectory) 0L else info.endOfFile,
                            lastModified = info.lastWriteTime?.toEpochMillis() ?: 0L,
                            mimeType = mime
                        )
                    }
                    .toList()
            } catch (t: Throwable) {
                resetConnection()
                throw FileSystemException(
                    "SMB 目录读取失败：${t.message ?: "请检查共享名与权限"}",
                    t
                )
            }
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        mutex.withLock {
            val share = ensureShare()
            val smbPath = toSmbPath(path)
            try {
                val file = share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                SmbInputStream(file)
            } catch (t: Throwable) {
                resetConnection()
                throw FileSystemException(
                    "SMB 文件读取失败：${t.message ?: "请检查共享名与权限"}",
                    t
                )
            }
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock { resetConnection() }
    }

    fun toSmbPath(path: String): String {
        val relative = FsPaths.normalize(path).trim('/')
        val combined = when {
            shareRoot.isEmpty() -> relative
            relative.isEmpty() -> shareRoot
            else -> "$shareRoot/$relative"
        }
        return combined.replace('/', '\\')
    }

    private fun ensureShare(): DiskShare {
        share?.let { return it }
        val config = SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withReadTimeout(45, TimeUnit.SECONDS)
            .withWriteTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
        val smbClient = SMBClient(config)
        val port = if (source.port > 0) source.port else SMBClient.DEFAULT_PORT
        try {
            val conn = smbClient.connect(source.host, port)
            val auth = if (source.username.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    source.username,
                    source.password.toCharArray(),
                    source.domain.takeIf { it.isNotBlank() }
                )
            }
            val sess = conn.authenticate(auth)
            val shareName = source.share.trim('/', '\\')
            if (shareName.isEmpty()) throw IOException("请填写 SMB 共享名")
            val diskShare = sess.connectShare(shareName) as? DiskShare
                ?: throw IOException("无法打开 SMB 共享：$shareName")
            client = smbClient
            connection = conn
            session = sess
            share = diskShare
            return diskShare
        } catch (t: Throwable) {
            runCatching { smbClient.close() }
            throw FileSystemException("SMB 连接失败：${t.message ?: "请检查服务器地址"}", t)
        }
    }

    private fun resetConnection() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        connection = null
        client = null
    }

    private class SmbInputStream(
        private val file: com.hierynomus.smbj.share.File
    ) : FilterInputStream(file.inputStream) {
        override fun close() {
            runCatching { super.close() }
            runCatching { file.close() }
        }
    }
}