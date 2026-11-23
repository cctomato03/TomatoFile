package com.cctomato.tomatofile.data.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.util.PathCodec
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream

/**
 * ExoPlayer DataSource for TomatoFile remote sources. It streams from the source's native protocol
 * (WebDAV Range, FTP REST, SMBJ seek) so 4K playback and seeking do not require downloading the
 * whole file into the cache first.
 */
class TomatoDataSource(private val context: Context) : BaseDataSource(true) {
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val dataUri = dataSpec.uri
        if (!TomatoMedia.SCHEME.equals(dataUri.scheme, ignoreCase = true)) {
            throw IOException("Unsupported media URI: $dataUri")
        }
        val sourceId = dataUri.getQueryParameter("source")
            ?: throw IOException("Missing source id")
        val path = PathCodec.decode(dataUri.getQueryParameter("path"))
        val declaredSize = dataUri.getQueryParameter("size")?.toLongOrNull() ?: 0L
        val source = context.tomatoContainer.sources.get(sourceId)
            ?: throw IOException("存储配置已不存在")
        val stream = try {
            runBlocking {
                context.tomatoContainer.repository.openStream(source, path, dataSpec.position)
            }
        } catch (t: Throwable) {
            throw IOException(t.message ?: "无法打开媒体流", t)
        }
        input = stream
        uri = dataUri
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            declaredSize > dataSpec.position -> declaredSize - dataSpec.position
            declaredSize > 0L -> 0L
            else -> C.LENGTH_UNSET.toLong()
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = input ?: throw IOException("Media stream is not open")
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val read = stream.read(buffer, offset, toRead)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val stream = input
        input = null
        uri = null
        bytesRemaining = C.LENGTH_UNSET.toLong()
        runCatching { stream?.close() }
        transferEnded()
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = TomatoDataSource(context)
    }
}

/** Builds the opaque URI consumed by [TomatoDataSource] and the matching ExoPlayer media item. */
object TomatoMedia {
    const val SCHEME = "tomatofs"

    fun uri(source: FileSource, entry: FileEntry): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority("stream")
        .appendQueryParameter("source", source.id)
        .appendQueryParameter("path", PathCodec.encode(entry.path))
        .appendQueryParameter("size", entry.size.toString())
        .build()

    fun mediaItem(source: FileSource, entry: FileEntry): MediaItem =
        MediaItem.Builder()
            .setMediaId("${source.id}:${entry.path}")
            .setUri(uri(source, entry))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(entry.name)
                    .build()
            )
            .build()
}