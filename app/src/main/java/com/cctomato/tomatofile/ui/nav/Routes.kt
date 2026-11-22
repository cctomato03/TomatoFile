package com.cctomato.tomatofile.ui.nav

import android.util.Base64

/** Route helpers. Paths are Base64 (URL-safe) encoded so slashes and unicode never confuse nav. */
object Routes {
    const val HOME = "home"
    const val ADD_SOURCE = "source?editId={editId}"
    const val BROWSE = "browse/{sourceId}?path={path}"
    const val PREVIEW = "preview/{sourceId}?path={path}"
    const val VIEWER = "viewer/{sourceId}?path={path}"
    const val CACHE = "cache"
    const val SETTINGS = "settings"

    fun addSource(editId: String = ""): String =
        if (editId.isBlank()) "source" else "source?editId=$editId"

    fun browse(sourceId: String, path: String): String =
        "browse/$sourceId?path=${encodePath(path)}"

    fun preview(sourceId: String, path: String): String =
        "preview/$sourceId?path=${encodePath(path)}"

    fun viewer(sourceId: String, path: String): String =
        "viewer/$sourceId?path=${encodePath(path)}"

    fun encodePath(path: String): String =
        Base64.encodeToString(
            path.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun decodePath(raw: String?): String {
        if (raw.isNullOrBlank()) return "/"
        return runCatching {
            String(
                Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
        }.getOrDefault("/")
    }
}