package com.cctomato.tomatofile.data.model

import android.webkit.MimeTypeMap
import java.util.Locale

/** Extension / MIME based classification used by the browser and the preview screens. */
object FileKinds {
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "ico", "jfif"
    )
    private val textExtensions = setOf(
        "txt", "text", "log", "md", "markdown", "json", "json5", "xml", "html", "htm", "xhtml",
        "css", "scss", "sass", "less", "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts",
        "java", "python", "py", "rb", "go", "rs", "c", "h", "cpp", "cc", "hpp", "cs", "php",
        "sh", "bash", "zsh", "bat", "cmd", "ps1", "yml", "yaml", "toml", "ini", "cfg", "conf",
        "properties", "gradle", "sql", "csv", "tsv", "srt", "vtt", "tex", "diff", "patch",
        "gitignore", "editorconfig", "dockerfile", "makefile", "pro", "env", "lock", "rtf"
    )
    private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts")
    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "jar", "iso", "tgz"
    )

    private val explicitMime = mapOf(
        "md" to "text/markdown",
        "json" to "application/json",
        "yaml" to "text/yaml",
        "yml" to "text/yaml",
        "toml" to "text/toml",
        "kt" to "text/x-kotlin",
        "kts" to "text/x-kotlin",
        "gradle" to "text/x-gradle",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "webp" to "image/webp",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "doc" to "application/msword",
        "xls" to "application/vnd.ms-excel",
        "ppt" to "application/vnd.ms-powerpoint"
    )

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun mimeForName(name: String): String {
        val ext = extension(name)
        explicitMime[ext]?.let { return it }
        if (ext.isNotEmpty()) {
            runCatching {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            }.getOrNull()?.let { return it }
        }
        return if (name == "Dockerfile" || name == "Makefile") {
            "text/plain"
        } else {
            "application/octet-stream"
        }
    }

    fun previewKind(name: String, mimeType: String = mimeForName(name)): PreviewKind {
        val ext = extension(name)
        return when {
            ext in imageExtensions || mimeType.startsWith("image/") -> PreviewKind.IMAGE
            ext == "pdf" || mimeType == "application/pdf" -> PreviewKind.PDF
            ext == "docx" || ext == "odt" -> PreviewKind.DOCX
            ext == "xlsx" || ext == "ods" -> PreviewKind.XLSX
            ext == "pptx" || ext == "odp" -> PreviewKind.PPTX
            ext in textExtensions || mimeType.startsWith("text/") -> PreviewKind.TEXT
            ext == "csv" -> PreviewKind.TEXT
            ext in audioExtensions || mimeType.startsWith("audio/") -> PreviewKind.AUDIO
            ext in videoExtensions || mimeType.startsWith("video/") -> PreviewKind.VIDEO
            ext in archiveExtensions -> PreviewKind.ARCHIVE
            else -> PreviewKind.OTHER
        }
    }

    fun isImageFile(name: String): Boolean =
        extension(name) in imageExtensions || mimeForName(name).startsWith("image/")
}