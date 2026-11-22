package com.cctomato.tomatofile

import com.cctomato.tomatofile.data.model.FsPaths
import org.junit.Assert.assertEquals
import org.junit.Test

class FsPathsTest {
    @Test
    fun normalizesAndRemovesTraversal() {
        assertEquals("/", FsPaths.normalize(""))
        assertEquals("/", FsPaths.normalize("/../../"))
        assertEquals("/b/c", FsPaths.normalize("/a/../b//c/"))
        assertEquals("/a b/c", FsPaths.normalize("/a b/c"))
    }

    @Test
    fun joinAndParentWorkAtRoot() {
        assertEquals("/", FsPaths.join("/", ""))
        assertEquals("/foo", FsPaths.join("/", "foo"))
        assertEquals("/foo/bar", FsPaths.join("/foo", "bar"))
        assertEquals("/foo", FsPaths.parent("/foo/bar"))
        assertEquals("/", FsPaths.parent("/foo"))
        assertEquals("/", FsPaths.parent("/"))
    }

    @Test
    fun segmentsAndNames() {
        assertEquals(listOf("a", "b c", "d"), FsPaths.segments("/a/b c/d"))
        assertEquals("d", FsPaths.name("/a/b c/d/"))
        assertEquals(true, FsPaths.isRoot("/"))
        assertEquals(false, FsPaths.isRoot("/a"))
    }
}