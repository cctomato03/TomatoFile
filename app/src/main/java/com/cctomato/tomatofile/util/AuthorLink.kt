package com.cctomato.tomatofile.util

import com.cctomato.tomatofile.data.model.ImageMetadata
import java.net.URLEncoder
import java.util.Locale

/**
 * Turns the author field of an image into a system-openable link. A real URL is used as-is;
 * a plain name (or an email address) becomes a web search so the link button always has a useful
 * destination when metadata exists.
 */
object AuthorLink {
    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val placeholders = setOf(
        "unknown", "unknown artist", "anonymous", "none", "null", "n/a", "na", "untitled", "-"
    )

    fun fromMetadata(metadata: ImageMetadata): String? {
        val artist = metadata.artist?.trim()?.takeIf { it.isNotBlank() && !isPlaceholder(it) }
        if (artist != null) {
            return normalize(artist)
        }
        val copyright = metadata.copyright?.trim()?.takeIf { it.isNotBlank() && !isPlaceholder(it) }
        if (copyright != null) {
            urlPattern.find(copyright)?.let { return it.value.trimEnd('.', ',', ';', ')', ']') }
        }
        return null
    }

    fun normalize(raw: String): String {
        val value = raw.trim()
        urlPattern.find(value)?.let { match ->
            return match.value.trimEnd('.', ',', ';', ')', ']')
        }
        val lowered = value.lowercase(Locale.ROOT)
        return when {
            lowered.startsWith("mailto:") -> value
            value.contains('@') && !value.contains(' ') -> "mailto:$value"
            lowered.startsWith("www.") -> "https://$value"
            value.matches(Regex("""^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/.*)?$""")) -> "https://$value"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
        }
    }

    private fun isPlaceholder(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT) in placeholders
}