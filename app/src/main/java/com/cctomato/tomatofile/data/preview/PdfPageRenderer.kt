package com.cctomato.tomatofile.data.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders PDF pages on demand with android.graphics.pdf.PdfRenderer. Rendering must be serialized
 * because a PdfRenderer can only have one page open at a time.
 */
class PdfPageRenderer(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    private val cache = LinkedHashMap<Int, Bitmap>(8, 0.75f, true)

    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, targetWidth: Int): Bitmap = mutex.withLock {
        cache[index]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            try {
                val safeWidth = targetWidth.coerceIn(320, 2400)
                val scale = safeWidth.toFloat() / page.width.toFloat()
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache[index] = bitmap
                while (cache.size > 4) {
                    val iterator = cache.entries.iterator()
                    if (!iterator.hasNext()) break
                    iterator.next()
                    iterator.remove()
                }
                bitmap
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        cache.clear()
    }
}