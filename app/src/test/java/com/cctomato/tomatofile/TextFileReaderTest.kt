package com.cctomato.tomatofile

import com.cctomato.tomatofile.data.preview.TextFileReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextFileReaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsUtf8TextAndNormalizesNewlines() {
        val file = temp.newFile("hello.txt")
        file.writeText("hello\r\nworld\r\n中文", Charsets.UTF_8)
        val content = TextFileReader.read(file)
        assertFalse(content.binary)
        assertEquals("hello\nworld\n中文", content.text)
        assertFalse(content.truncated)
    }

    @Test
    fun supportsUtf16WithBom() {
        val file = temp.newFile("utf16.txt")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "你好 UTF-16".toByteArray(Charsets.UTF_16LE))
        val content = TextFileReader.read(file)
        assertFalse(content.binary)
        assertEquals("UTF-16LE", content.charset)
        assertTrue(content.text.contains("你好 UTF-16"))
    }

    @Test
    fun detectsBinary() {
        val file = temp.newFile("blob.bin")
        file.writeBytes(byteArrayOf(0, 1, 2, 3, 0, 5, 0, 7, 9, 0))
        val content = TextFileReader.read(file)
        assertTrue(content.binary)
    }

    @Test
    fun marksLargeFilesAsTruncated() {
        val file = temp.newFile("big.txt")
        file.writeText("abcdefghij", Charsets.UTF_8)
        val content = TextFileReader.read(file, maxBytes = 5)
        assertTrue(content.truncated)
        assertEquals("abcde", content.text)
    }
}