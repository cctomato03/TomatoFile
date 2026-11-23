package com.cctomato.tomatofile.util

import android.util.Base64

/** Base64 URL-safe path codec used by navigation routes and the media DataSource URI. */
object PathCodec {
    fun encode(path: String): String =
        Base64.encodeToString(
            path.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun decode(raw: String?): String {
        if (raw.isNullOrBlank()) return "/"
        return runCatching {
            String(
                Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
        }.getOrDefault("/")
    }
}