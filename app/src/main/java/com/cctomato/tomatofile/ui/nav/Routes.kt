package com.cctomato.tomatofile.ui.nav

import com.cctomato.tomatofile.util.PathCodec

/** Route helpers. Paths are Base64 (URL-safe) encoded so slashes and unicode never confuse nav. */
object Routes {
    const val HOME = "home"
    const val ADD_SOURCE = "source?editId={editId}"
    const val BROWSE = "browse/{sourceId}?path={path}"
    const val PREVIEW = "preview/{sourceId}?path={path}"
    const val VIEWER = "viewer/{sourceId}?path={path}"
    const val PLAYER = "player/{sourceId}?path={path}&kind={kind}"
    const val CACHE = "cache"
    const val SETTINGS = "settings"

    fun addSource(editId: String = ""): String =
        if (editId.isBlank()) "source" else "source?editId=$editId"

    fun browse(sourceId: String, path: String): String =
        "browse/$sourceId?path=${PathCodec.encode(path)}"

    fun preview(sourceId: String, path: String): String =
        "preview/$sourceId?path=${PathCodec.encode(path)}"

    fun viewer(sourceId: String, path: String): String =
        "viewer/$sourceId?path=${PathCodec.encode(path)}"

    fun player(sourceId: String, path: String, kind: String): String =
        "player/$sourceId?path=${PathCodec.encode(path)}&kind=$kind"

    fun encodePath(path: String): String = PathCodec.encode(path)

    fun decodePath(raw: String?): String = PathCodec.decode(raw)
}