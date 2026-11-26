package com.cctomato.tomatofile.data.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Fast duration probe for MPEG-TS / BDAV (m2ts) files.
 *
 * Android's MediaMetadataRetriever and libVLC often report a zero duration for Blu-ray BDAV
 * streams, even though the stream contains PCR timestamps. Reading the first and last PCR from
 * the file gives a reliable duration without scanning several gigabytes.
 */
object MpegTsDuration {
    private const val PCR_WRAP = (1L shl 33) * 300L // 27 MHz clock, 33-bit base
    private const val SAMPLE_BYTES = 8 * 1024 * 1024
    private const val MAX_TAIL_BYTES = 64 * 1024 * 1024

    suspend fun probe(file: File): Long? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.isFile || file.length() < 4096L) return@runCatching null
            RandomAccessFile(file, "r").use { raf ->
                val packetSize = detectPacketSize(raf) ?: return@runCatching null
                val first = findPcr(raf, 0L, minOf(file.length(), SAMPLE_BYTES.toLong()), packetSize)
                    ?: return@runCatching null
                var last: Long? = null
                var tail = SAMPLE_BYTES.toLong()
                while (last == null && tail <= MAX_TAIL_BYTES) {
                    val start = (file.length() - tail).coerceAtLeast(0L)
                    last = findPcr(raf, start, file.length(), packetSize, lastInChunk = true)
                    tail *= 2
                }
                if (last == null) return@runCatching null
                var duration = last - first
                if (duration < 0) duration += PCR_WRAP
                val millis = duration / 27_000L
                if (millis in 1_000L..(24L * 60L * 60L * 1000L)) millis else null
            }
        }.getOrNull()
    }

    private fun detectPacketSize(raf: RandomAccessFile): Int? {
        val sample = ByteArray(192 * 32)
        val read = raf.read(sample)
        if (read < 192 * 4) return null
        // BDAV uses 192-byte packets with a 4-byte prefix; plain TS uses 188.
        val score192 = (0 until 24).count { i ->
            val offset = i * 192 + 4
            offset < read && sample[offset] == 0x47.toByte()
        }
        val score188 = (0 until 24).count { i ->
            val offset = i * 188
            offset < read && sample[offset] == 0x47.toByte()
        }
        return when {
            score192 >= score188 && score192 >= 12 -> 192
            score188 >= 12 -> 188
            score192 > score188 -> 192
            else -> 188
        }
    }

    private fun findPcr(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        packetSize: Int,
        lastInChunk: Boolean = false
    ): Long? {
        val aligned = (start / packetSize) * packetSize
        val length = (end - aligned).coerceAtMost(SAMPLE_BYTES.toLong()).toInt()
        if (length <= 0) return null
        val buffer = ByteArray(length)
        raf.seek(aligned)
        val read = raf.read(buffer)
        var found: Long? = null
        var packetStart = 0
        while (packetStart + packetSize <= read) {
            val syncOffset = packetStart + if (packetSize == 192) 4 else 0
            if (buffer[syncOffset] != 0x47.toByte()) {
                packetStart += packetSize
                continue
            }
            val afc = (buffer[syncOffset + 3].toInt() ushr 4) and 0x03
            if (afc == 2 || afc == 3) {
                val adaptLength = buffer[syncOffset + 4].toInt() and 0xff
                if (adaptLength >= 7 && (buffer[syncOffset + 5].toInt() and 0x10) != 0) {
                    val p = syncOffset + 6
                    val base = ((buffer[p].toLong() and 0xff) shl 25) or
                        ((buffer[p + 1].toLong() and 0xff) shl 17) or
                        ((buffer[p + 2].toLong() and 0xff) shl 9) or
                        ((buffer[p + 3].toLong() and 0xff) shl 1) or
                        ((buffer[p + 4].toLong() and 0xfe) ushr 7)
                    val ext = ((buffer[p + 4].toLong() and 0x01) shl 8) or
                        (buffer[p + 5].toLong() and 0xff)
                    val pcr = base * 300L + ext
                    if (lastInChunk) found = pcr else return pcr
                }
            }
            packetStart += packetSize
        }
        return found
    }
}
