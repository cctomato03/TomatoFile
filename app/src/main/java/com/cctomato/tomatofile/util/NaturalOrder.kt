package com.cctomato.tomatofile.util

/** Orders "IMG_2" before "IMG_10", which is what users expect when swiping through photos. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ni = i
                var nj = j
                while (ni < a.length && a[ni].isDigit()) ni++
                while (nj < b.length && b[nj].isDigit()) nj++
                val na = a.substring(i, ni).trimStart('0')
                val nb = b.substring(j, nj).trimStart('0')
                val cmp = when {
                    na.length != nb.length -> na.length.compareTo(nb.length)
                    else -> na.compareTo(nb)
                }
                if (cmp != 0) return cmp
                i = ni
                j = nj
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        val remaining = (a.length - i).compareTo(b.length - j)
        return if (remaining != 0) remaining else a.compareTo(b, ignoreCase = true)
    }
}