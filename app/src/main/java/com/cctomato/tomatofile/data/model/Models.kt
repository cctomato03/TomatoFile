package com.cctomato.tomatofile.data.model

import java.util.Locale
import java.util.UUID

/** The kinds of file systems TomatoFile can talk to. */
enum class SourceType(val label: String) {
    LOCAL("本机"),
    WEBDAV("WebDAV"),
    SMB("SMB"),
    FTP("FTP");

    companion object {
        fun fromName(value: String?): SourceType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOCAL
    }
}

/**
 * A configured file system. Paths inside a source are always relative, normalized and start
 * with '/'. This keeps navigation, cache keys and previews independent from the backing protocol.
 */
data class FileSource(
    val id: String = UUID.randomUUID().toString(),
    val type: SourceType = SourceType.LOCAL,
    val name: String = "",
    /** Absolute path for LOCAL sources. */
    val localPath: String = "",
    /** Base URL for WEBDAV sources (may include a path). */
    val url: String = "",
    /** Host for SMB / FTP. */
    val host: String = "",
    /** SMB / FTP port. 0 means the protocol default. */
    val port: Int = 0,
    /** SMB share name. */
    val share: String = "",
    /** Base folder inside the remote file system. Defaults to '/'. */
    val rootPath: String = "",
    /** Windows / SMB domain. */
    val domain: String = "",
    val username: String = "",
    val password: String = "",
    /** Explicit FTPS for FTP sources. */
    val secure: Boolean = false,
    /** The seeded local source cannot be removed. */
    val builtIn: Boolean = false
) {
    fun normalizedRoot(): String {
        val raw = when (type) {
            SourceType.LOCAL -> ""
            SourceType.SMB -> sharePath()
            SourceType.FTP -> rootPath
            SourceType.WEBDAV -> ""
        }
        return FsPaths.normalize(raw)
    }

    fun sharePath(): String = FsPaths.normalize("/" + share.trim('/') + "/" + rootPath.trim('/'))

    fun summary(): String = when (type) {
        SourceType.LOCAL -> localPath.ifBlank { "应用存储" }
        SourceType.WEBDAV -> url
        SourceType.SMB -> "smb://${host}${if (port > 0 && port != 445) ":$port" else ""}/${share.trim('/')}"
        SourceType.FTP -> "${if (secure) "ftps" else "ftp"}://${host}${if (port > 0 && port != 21) ":$port" else ""}"
    }
}

/** A single directory entry, with a source-relative normalized path. */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String = "application/octet-stream"
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    val kind: PreviewKind
        get() = if (isDirectory) PreviewKind.FOLDER else FileKinds.previewKind(name, mimeType)

    val isImage: Boolean
        get() = !isDirectory && kind == PreviewKind.IMAGE
}

/** How a file should be presented inside the app. */
enum class PreviewKind {
    FOLDER,
    IMAGE,
    PDF,
    TEXT,
    DOCX,
    XLSX,
    PPTX,
    AUDIO,
    VIDEO,
    ARCHIVE,
    OTHER
}

/** Normalization helpers shared by every file system implementation. */
object FsPaths {
    /** Returns an absolute-style path with a leading slash and no trailing slash (except root). */
    fun normalize(path: String): String {
        val raw = path.replace('\\', '/')
        val parts = ArrayDeque<String>()
        for (segment in raw.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun join(parent: String, name: String): String {
        if (name.isEmpty()) return normalize(parent)
        val base = normalize(parent).trimEnd('/')
        return normalize("$base/$name")
    }

    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == "/") return "/"
        val index = normalized.lastIndexOf('/')
        return if (index <= 0) "/" else normalized.substring(0, index)
    }

    fun name(path: String): String = normalize(path).substringAfterLast('/')

    fun segments(path: String): List<String> = normalize(path).split('/').filter { it.isNotEmpty() }

    fun isRoot(path: String): Boolean = normalize(path) == "/"
}

/** Sort options exposed in the browser overflow menu. */
enum class SortMode(val label: String) {
    NAME_ASC("名称 A → Z"),
    NAME_DESC("名称 Z → A"),
    DATE_DESC("最新修改"),
    SIZE_DESC("文件大小"),
    TYPE("文件类型");

    companion object {
        fun fromName(value: String?): SortMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NAME_ASC
    }
}

enum class ViewMode { LIST, GRID }

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

data class CacheStats(
    val totalBytes: Long = 0L,
    val maxBytes: Long = 0L,
    val itemCount: Int = 0,
    val perSource: Map<String, Long> = emptyMap()
)

data class ImageMetadata(
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val dateTime: String? = null,
    val camera: String? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val description: String? = null,
    val location: String? = null,
    val software: String? = null
)