package com.cctomato.tomatofile

import com.cctomato.tomatofile.util.NaturalOrder
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun sortsNumbersNaturally() {
        val names = listOf("photo_10.jpg", "photo_2.jpg", "photo_1.jpg", "photo_20.jpg")
        val sorted = names.sortedWith(NaturalOrder)
        assertTrue(sorted.indexOf("photo_2.jpg") < sorted.indexOf("photo_10.jpg"))
        assertTrue(sorted.indexOf("photo_10.jpg") < sorted.indexOf("photo_20.jpg"))
    }

    @Test
    fun caseInsensitiveOrdering() {
        assertTrue(NaturalOrder.compare("apple", "Banana") < 0)
        assertTrue(NaturalOrder.compare("Apple", "apple") <= 0)
    }
}