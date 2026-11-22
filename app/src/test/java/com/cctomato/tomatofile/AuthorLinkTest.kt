package com.cctomato.tomatofile

import com.cctomato.tomatofile.data.model.ImageMetadata
import com.cctomato.tomatofile.util.AuthorLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun plainNameDoesNotBecomeSearchLink() {
        assertNull(AuthorLink.fromMetadata(ImageMetadata(artist = "Jane Doe")))
        assertNull(AuthorLink.protocolLink("Jane Doe"))
    }

    @Test
    fun nakedEmailHasNoProtocol() {
        assertNull(AuthorLink.protocolLink("hi@example.com"))
    }

    @Test
    fun domainWithoutSchemeHasNoProtocol() {
        assertNull(AuthorLink.protocolLink("example.com"))
        assertNull(AuthorLink.protocolLink("www.example.com"))
    }

    @Test
    fun explicitOpaqueProtocolsAreKept() {
        assertEquals("mailto:hi@example.com", AuthorLink.protocolLink("mailto:hi@example.com"))
        assertEquals("tel:+8613800000000", AuthorLink.protocolLink("tel:+8613800000000"))
        assertEquals("sms:+8613800000000", AuthorLink.protocolLink("sms:+8613800000000"))
    }

    @Test
    fun customSchemeIsKept() {
        assertEquals("myapp://author/42", AuthorLink.protocolLink("myapp://author/42"))
        assertEquals(
            "myapp://author/42",
            AuthorLink.protocolLink("Photo by Jane (myapp://author/42)")
        )
    }

    @Test
    fun unsafeSchemesAreBlocked() {
        assertNull(AuthorLink.protocolLink("file:///etc/passwd"))
        assertNull(AuthorLink.protocolLink("content://com.example/secret"))
        assertNull(AuthorLink.protocolLink("javascript:alert(1)"))
    }

    @Test
    fun trailingPunctuationIsTrimmed() {
        assertEquals(
            "https://example.com/author",
            AuthorLink.protocolLink("https://example.com/author).")
        )
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

    @Test
    fun plainCopyrightDoesNotBecomeLink() {
        val meta = ImageMetadata(artist = "Jane Doe", copyright = "© 2026 Jane Doe")
        assertNull(AuthorLink.fromMetadata(meta))
    }
}