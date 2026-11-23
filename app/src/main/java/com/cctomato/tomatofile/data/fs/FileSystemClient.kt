package com.cctomato.tomatofile.data.fs

import com.cctomato.tomatofile.data.model.FileEntry
import java.io.InputStream

/** Shared blocking-friendly contract implemented by every remote / local backend. */
interface FileSystemClient {
    suspend fun list(path: String): List<FileEntry>
    suspend fun openStream(path: String): InputStream

    /**
     * Opens a stream starting at [position]. Backends with native range support override this;
     * the default skips forward, which is enough for SMBJ's seekable input stream.
     */
    suspend fun openStream(path: String, position: Long): InputStream {
        val input = openStream(path)
        if (position <= 0L) return input
        var remaining = position
        val buffer = ByteArray(8 * 1024)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            remaining -= read
        }
        return input
    }

    suspend fun close()
}

/** Human friendly error shown by the UI. */
class FileSystemException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun Throwable.friendlyMessage(fallback: String = "操作失败"): String {
    val raw = message?.trim().orEmpty()
    return when {
        raw.isBlank() -> fallback
        raw.contains("timeout", ignoreCase = true) -> "连接超时，请检查地址与网络"
        raw.contains("Unable to resolve host", ignoreCase = true) -> "无法解析服务器地址"
        raw.contains("EHOSTUNREACH", ignoreCase = true) -> "无法连接到服务器"
        raw.contains("Connection refused", ignoreCase = true) -> "服务器拒绝连接"
        raw.contains("401", ignoreCase = true) -> "认证失败，请检查用户名和密码"
        raw.contains("403", ignoreCase = true) -> "没有访问权限"
        raw.contains("404", ignoreCase = true) -> "路径不存在"
        else -> raw
    }
}