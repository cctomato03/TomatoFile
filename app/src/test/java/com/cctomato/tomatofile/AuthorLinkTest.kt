package com.cctomato.tomatofile

import com.cctomato.tomatofile.data.model.ImageMetadata
import com.cctomato.tomatofile.util.AuthorLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorLinkTest {
    @Test
    fun keepsAuthorUrlAsIs() {
        val meta = ImageMetadata(artist = "https://example.com/author")
        assertEquals("https://example.com/author", AuthorLink.fromMetadata(meta))
    }

    @Test
    fun extractsUrlFromMixedAuthorText() {
        val meta = ImageMetadata(artist = "Photo by Jane (https://jane.example.com)")
        assertEquals("https://jane.example.com", AuthorLink.fromMetadata(meta))
    }

    @Test
    fun turnsPlainNameIntoSearch() {
        val link = AuthorLink.fromMetadata(ImageMetadata(artist = "Jane Doe"))
        assertTrue(link!!.startsWith("https://www.google.com/search?q="))
        assertTrue(link.contains("Jane+Doe"))
    }

    @Test
    fun emailBecomesMailto() {
        assertEquals("mailto:hi@example.com", AuthorLink.normalize("hi@example.com"))
    }

    @Test
    fun domainBecomesHttps() {
        assertEquals("https://example.com", AuthorLink.normalize("example.com"))
    }

    @Test
    fun placeholderIsIgnored() {
        assertNull(AuthorLink.fromMetadata(ImageMetadata(artist = "unknown")))
    }

    @Test
    fun copyrightUrlIsFallback() {
        val meta = ImageMetadata(artist = "unknown", copyright = "© 2026 https://example.com/license")
        assertEquals("https://example.com/license", AuthorLink.fromMetadata(meta))
    }
}