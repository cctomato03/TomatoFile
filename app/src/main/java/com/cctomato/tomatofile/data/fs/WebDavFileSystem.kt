package com.cctomato.tomatofile.data.fs

import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Minimal but practical WebDAV client built on OkHttp. It supports Basic authentication,
 * PROPFIND depth-1 listings and streaming GETs, which covers Nextcloud, ownCloud, Alist, Synology
 * and most Apache/nginx WebDAV servers.
 */
class WebDavFileSystem(private val source: FileSource) : FileSystemClient {

    private val baseUrl: HttpUrl = normalizeBaseUrl(source.url)
    private val baseSegments = baseUrl.pathSegments.filter { it.isNotEmpty() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val relative = FsPaths.normalize(path).trim('/')
        val directoryUrl = if (relative.isEmpty()) {
            baseUrl
        } else {
            baseUrl.resolve("$relative/") ?: throw FileSystemException("WebDAV 路径无效")
        }
        val request = Request.Builder()
            .url(directoryUrl)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .header("Accept", "application/xml, text/xml")
            .applyAuth()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw FileSystemException(httpError(response.code, response.message))
            }
            val xml = response.body?.string().orEmpty()
            parseListing(xml, directoryUrl)
        }
    }

    override suspend fun openStream(path: String): InputStream = openStream(path, 0L)

    override suspend fun openStream(path: String, position: Long): InputStream =
        withContext(Dispatchers.IO) {
            val relative = FsPaths.normalize(path).trim('/')
            val url = if (relative.isEmpty()) {
                baseUrl
            } else {
                baseUrl.resolve(relative) ?: throw FileSystemException("WebDAV 路径无效")
            }
            val builder = Request.Builder()
                .url(url)
                .get()
                .header("Accept-Encoding", "identity")
                .applyAuth()
            if (position > 0L) builder.header("Range", "bytes=$position-")
            val response = client.newCall(builder.build()).execute()
            if (response.code == 416) {
                response.close()
                throw FileSystemException("无法从该位置继续读取")
            }
            if (!response.isSuccessful) {
                val code = response.code
                val message = response.message
                response.close()
                throw FileSystemException(httpError(code, message))
            }
            val body = response.body ?: run {
                response.close()
                throw FileSystemException("服务器返回了空内容")
            }
            val stream = body.byteStream()
            if (position > 0L && response.code == 200) {
                // Some WebDAV servers ignore Range; skip the already-sent prefix instead.
                var remaining = position
                val buffer = ByteArray(16 * 1024)
                while (remaining > 0L) {
                    val skipped = stream.skip(remaining)
                    if (skipped > 0L) {
                        remaining -= skipped
                        continue
                    }
                    val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    remaining -= read
                }
            }
            ResponseInputStream(response, stream)
        }

    override suspend fun close() {
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }

    private fun parseListing(xml: String, directoryUrl: HttpUrl): List<FileEntry> {
        if (xml.isBlank()) return emptyList()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        val responses = ArrayList<DavResponse>()
        var current: DavResponse? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (localName(parser)) {
                    "response" -> current = DavResponse()
                    "href" -> current?.href = parser.nextText().trim()
                    "collection" -> current?.isDirectory = true
                    "getcontentlength" -> current?.size =
                        parser.nextText().trim().toLongOrNull() ?: 0L
                    "getlastmodified" -> current?.modifiedRaw = parser.nextText().trim()
                    "creationdate" -> current?.creationRaw = parser.nextText().trim()
                    "getcontenttype" -> current?.mimeType = parser.nextText().trim()
                    "displayname" -> current?.displayName = parser.nextText().trim()
                }
            } else if (event == XmlPullParser.END_TAG && localName(parser) == "response") {
                current?.let(responses::add)
                current = null
            }
            event = parser.next()
        }

        val directorySegments = directoryUrl.pathSegments.filter { it.isNotEmpty() }
        val result = LinkedHashMap<String, FileEntry>()
        for (response in responses) {
            val href = response.href ?: continue
            val target = directoryUrl.resolve(href) ?: continue
            val targetSegments = target.pathSegments.filter { it.isNotEmpty() }
            if (targetSegments == directorySegments) continue
            if (targetSegments.size <= baseSegments.size) continue
            if (targetSegments.take(baseSegments.size) != baseSegments) continue

            val relSegments = targetSegments.drop(baseSegments.size)
            if (relSegments.isEmpty()) continue
            val relativePath = FsPaths.normalize("/" + relSegments.joinToString("/"))
            val name = response.displayName?.takeIf { it.isNotBlank() }
                ?: relSegments.lastOrNull()
                ?: FsPaths.name(relativePath)
            if (name == "." || name == "..") continue
            val mime = response.mimeType?.takeIf { it.isNotBlank() }
                ?: if (response.isDirectory) "inode/directory" else FileKinds.mimeForName(name)
            val modified = parseDate(response.modifiedRaw) ?: parseDate(response.creationRaw) ?: 0L

            result[relativePath] = FileEntry(
                name = name,
                path = relativePath,
                isDirectory = response.isDirectory,
                size = if (response.isDirectory) 0L else response.size,
                lastModified = modified,
                mimeType = mime
            )
        }
        return result.values.toList()
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        if (source.username.isNotBlank()) {
            header(
                "Authorization",
                Credentials.basic(source.username, source.password, Charsets.UTF_8)
            )
        }
        return header("User-Agent", "TomatoFile/1.0 (Android)")
    }

    private fun normalizeBaseUrl(raw: String): HttpUrl {
        var value = raw.trim()
        if (value.isEmpty()) throw FileSystemException("请填写 WebDAV 地址")
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "https://$value"
        }
        if (!value.endsWith("/")) value += "/"
        return value.toHttpUrlOrNull()
            ?: throw FileSystemException("WebDAV 地址格式不正确")
    }

    private fun localName(parser: XmlPullParser): String =
        parser.name?.substringAfter(':').orEmpty()

    private fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        runCatching {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        }
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(value).toEpochMilli() }
        return null
    }

    private fun httpError(code: Int, message: String): String = when (code) {
        401 -> "认证失败（401），请检查用户名和密码"
        403 -> "没有访问权限（403）"
        404 -> "路径不存在（404）"
        405 -> "服务器不支持 WebDAV PROPFIND（405）"
        in 500..599 -> "服务器错误（$code）"
        else -> "WebDAV 请求失败：$code $message"
    }

    private class DavResponse(
        var href: String? = null,
        var isDirectory: Boolean = false,
        var size: Long = 0L,
        var modifiedRaw: String? = null,
        var creationRaw: String? = null,
        var mimeType: String? = null,
        var displayName: String? = null
    )

    private class ResponseInputStream(
        private val response: Response,
        input: InputStream
    ) : FilterInputStream(input) {
        override fun close() {
            runCatching { super.close() }
            response.close()
        }
    }

    private companion object {
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getcontenttype/>
    <d:creationdate/>
  </d:prop>
</d:propfind>"""
    }
}