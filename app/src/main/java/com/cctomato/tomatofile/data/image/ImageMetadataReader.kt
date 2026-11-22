package com.cctomato.tomatofile.data.image

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.ImageMetadata
import java.io.File
import java.util.Locale

/** Reads dimensions and EXIF/XMP fields, including the author used by the image viewer link button. */
object ImageMetadataReader {

    fun read(file: File, nameHint: String? = null): ImageMetadata {
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val bounds = runCatching {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
                .also { BitmapFactory.decodeFile(file.absolutePath, it) }
        }.getOrNull()

        val exifWidth = maxOf(
            exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0) ?: 0,
            exif?.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0) ?: 0
        )
        val exifHeight = maxOf(
            exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0) ?: 0,
            exif?.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0) ?: 0
        )

        val make = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
        val model = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
        val camera = listOf(make, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val latLong = runCatching { exif?.getLatLong() }.getOrNull()
        val location = latLong?.takeIf { it.size >= 2 }?.let {
            String.format(Locale.US, "%.5f, %.5f", it[0], it[1])
        }

        return ImageMetadata(
            width = exifWidth.takeIf { it > 0 } ?: bounds?.outWidth ?: 0,
            height = exifHeight.takeIf { it > 0 } ?: bounds?.outHeight ?: 0,
            mimeType = FileKinds.mimeForName(nameHint ?: file.name),
            dateTime = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)?.trim()?.takeIf { it.isNotBlank() },
            camera = camera,
            artist = exif?.getAttribute(ExifInterface.TAG_ARTIST)?.trim()?.takeIf { it.isNotBlank() },
            copyright = exif?.getAttribute(ExifInterface.TAG_COPYRIGHT)?.trim()
                ?.takeIf { it.isNotBlank() },
            description = exif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.trim()
                ?.takeIf { it.isNotBlank() },
            location = location,
            software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()
                ?.takeIf { it.isNotBlank() }
        )
    }
}