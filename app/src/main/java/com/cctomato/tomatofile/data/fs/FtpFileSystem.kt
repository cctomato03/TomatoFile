package com.cctomato.tomatofile.data.fs

import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration

/**
 * Plain FTP / explicit FTPS. A fresh control connection is opened for each operation; this keeps
 * browsing resilient when servers idle out connections and makes concurrent downloads safe.
 */
class FtpFileSystem(private val source: FileSource) : FileSystemClient {

    private val rootRemote: String = FsPaths.normalize(
        source.rootPath.ifBlank { "/" }
    )

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        withConnection { ftp ->
            val remote = remotePath(path)
            val files = try {
                ftp.listFiles(remote)
            } catch (t: Throwable) {
                throw FileSystemException("FTP 目录读取失败：${t.message}", t)
            } ?: throw FileSystemException("FTP 目录读取失败：${ftp.replyString}")

            files.asSequence()
                .filter { it.name != "." && it.name != ".." }
                .map { file ->
                    val isDirectory = file.isDirectory
                    FileEntry(
                        name = file.name,
                        path = FsPaths.join(path, file.name),
                        isDirectory = isDirectory,
                        size = if (isDirectory) 0L else file.size,
                        lastModified = file.timestamp?.timeInMillis ?: 0L,
                        mimeType = if (isDirectory) "inode/directory"
                        else FileKinds.mimeForName(file.name)
                    )
                }
                .toList()
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        val ftp = createClient()
        try {
            connectAndLogin(ftp)
            val remote = remotePath(path)
            val stream = ftp.retrieveFileStream(remote)
                ?: throw FileSystemException("FTP 文件读取失败：${ftp.replyString}")
            FtpInputStream(ftp, stream)
        } catch (t: Throwable) {
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
            if (t is FileSystemException) throw t
            throw FileSystemException("FTP 文件读取失败：${t.message}", t)
        }
    }

    override suspend fun close() = Unit

    private fun remotePath(path: String): String {
        val relative = FsPaths.normalize(path).trim('/')
        val combined = when {
            rootRemote == "/" -> "/$relative"
            relative.isEmpty() -> rootRemote
            else -> "${rootRemote.trimEnd('/')}/$relative"
        }
        return FsPaths.normalize(combined)
    }

    private fun <T> withConnection(block: (FTPClient) -> T): T {
        val ftp = createClient()
        try {
            connectAndLogin(ftp)
            return block(ftp)
        } catch (t: Throwable) {
            if (t is FileSystemException) throw t
            throw FileSystemException("FTP 连接失败：${t.message ?: ftp.replyString}", t)
        } finally {
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
        }
    }

    private fun createClient(): FTPClient {
        return if (source.secure) {
            FTPSClient(false)
        } else {
            FTPClient()
        }.apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            defaultTimeout = DEFAULT_TIMEOUT_MS
            setDataTimeout(Duration.ofSeconds(45))
            controlEncoding = "UTF-8"
            setAutodetectUTF8(true)
            listHiddenFiles = true
            bufferSize = 64 * 1024
        }
    }

    private fun connectAndLogin(ftp: FTPClient) {
        val port = if (source.port > 0) source.port else 21
        ftp.connect(source.host, port)
        if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
            throw IOException("服务器拒绝连接：${ftp.replyString}")
        }
        if (!ftp.login(
                source.username.ifBlank { "anonymous" },
                source.password.ifBlank {
                    if (source.username.isBlank()) "anonymous@" else ""
                }
            )
        ) {
            throw FileSystemException("FTP 认证失败：${ftp.replyString}")
        }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        if (source.secure) {
            val tls = ftp as FTPSClient
            tls.execPBSZ(0)
            tls.execPROT("P")
        }
    }

    private class FtpInputStream(
        private val ftp: FTPClient,
        input: InputStream
    ) : FilterInputStream(input) {
        override fun close() {
            runCatching { super.close() }
            runCatching { ftp.completePendingCommand() }
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_TIMEOUT_MS = 25_000
    }
}