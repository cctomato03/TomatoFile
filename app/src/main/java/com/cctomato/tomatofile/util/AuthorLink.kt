package com.cctomato.tomatofile.util

import com.cctomato.tomatofile.data.model.ImageMetadata
import java.util.Locale

/**
 * Extracts a protocol-bearing link from image metadata. A plain author name is NOT turned into a
 * search URL: if the metadata contains something like `https://…`, `mailto:…` or a custom
 * `app://…` scheme, it is passed through unchanged to the system so the matching app can open it.
 */
object AuthorLink {
    /** The whole value is one URI, e.g. `https://example.com/a` or `myapp://author/42`. */
    private val fullUriPattern = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:[^\s<>"']+$""")

    /** Finds a URI embedded in a sentence, e.g. `Photo by Jane (https://jane.example.com)`. */
    private val embeddedUriPattern = Regex(
        """(?:[A-Za-z][A-Za-z0-9+.-]*://|(?:mailto|tel|sms|geo|urn):)[^\s<>"']+""",
        RegexOption.IGNORE_CASE
    )

    /** Schemes that must never be launched from untrusted image metadata. */
    private val blockedSchemes = setOf(
        "file", "content", "javascript", "data", "intent", "android.resource"
    )

    private val placeholders = setOf(
        "unknown", "unknown artist", "anonymous", "none", "null", "n/a", "na", "untitled", "-"
    )

    fun fromMetadata(metadata: ImageMetadata): String? {
        val artist = metadata.artist?.trim()?.takeIf { it.isNotBlank() && !isPlaceholder(it) }
        if (artist != null) {
            protocolLink(artist)?.let { return it }
        }
        val copyright = metadata.copyright?.trim()?.takeIf { it.isNotBlank() && !isPlaceholder(it) }
        if (copyright != null) {
            protocolLink(copyright)?.let { return it }
        }
        return null
    }

    /**
     * Returns [raw] (or the URI embedded in it) only when it carries a scheme. Plain names,
     * bare domains and naked email addresses deliberately return null instead of a search link.
     */
    fun protocolLink(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val candidate = when {
            fullUriPattern.matches(value) -> value
            else -> embeddedUriPattern.find(value)?.value ?: return null
        }.trimEnd(
            '.', ',', ';', ')', ']', '}', '>', '"', '\'',
            '。', '，', '；', '）', '】', '》'
        )
        if (candidate.isEmpty()) return null
        val scheme = candidate.substringBefore(':').lowercase(Locale.ROOT)
        if (scheme.isBlank() || scheme in blockedSchemes) return null
        return candidate
    }

    private fun isPlaceholder(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT) in placeholders
}