package com.cctomato.tomatofile.data.preview

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

data class TextContent(
    val text: String,
    val truncated: Boolean,
    val binary: Boolean,
    val charset: String
)

/** Reads a text file with a byte cap and a pragmatic UTF-8 → GB18030 fallback. */
object TextFileReader {
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun read(file: File, maxBytes: Int = MAX_BYTES): TextContent {
        val total = file.length()
        val truncated = total > maxBytes
        val bytes = file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var remaining = maxBytes
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.toByteArray()
        }
        if (bytes.isEmpty()) return TextContent("", truncated, false, "UTF-8")

        val nulCount = bytes.take(minOf(bytes.size, 16 * 1024)).count { it == 0.toByte() }
        if (nulCount > 4) {
            return TextContent(
                text = "此文件看起来是二进制文件，无法以文本方式预览。",
                truncated = truncated,
                binary = true,
                charset = ""
            )
        }

        val (charset, offset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> "UTF-8" to 3
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                "UTF-16LE" to 2
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                "UTF-16BE" to 2
            else -> "UTF-8" to 0
        }

        var text = String(bytes, offset, bytes.size - offset, Charset.forName(charset))
        if (charset == "UTF-8") {
            val replacementCount = text.count { it == '\uFFFD' }
            if (replacementCount > 2 && replacementCount * 200 > text.length) {
                text = runCatching {
                    String(bytes, offset, bytes.size - offset, Charset.forName("GB18030"))
                }.getOrDefault(text)
            }
            if (!truncated) {
                text = text.trimEnd('\uFFFD')
            }
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "")
        return TextContent(text, truncated, false, charset)
    }
}