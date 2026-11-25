package com.cctomato.tomatofile.ui.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.UUID

data class BridgeDraft(
    val type: String,
    val name: String,
    val localPath: String,
    val url: String,
    val host: String,
    val port: String,
    val share: String,
    val rootPath: String,
    val domain: String,
    val username: String,
    val password: String,
    val secure: Boolean
)

/**
 * A tiny, token-protected LAN page used by the TV source setup. It lives only while the QR
 * dialog is open and accepts exactly the fields the normal add-source form understands.
 */
class SourceBridgeServer private constructor(
    port: Int,
    private val onDraft: (BridgeDraft) -> Unit
) : NanoHTTPD(port) {

    private val token = UUID.randomUUID().toString().replace("-", "").take(18)
    private val mainHandler = Handler(Looper.getMainLooper())

    val pageUrl: String
        get() {
            val ip = localIpv4Address() ?: return ""
            return "http://$ip:$listeningPort/?token=$token"
        }

    override fun serve(session: IHTTPSession): Response {
        val supplied = session.parameters["token"]?.firstOrNull()
        if (supplied != token) {
            return textResponse(Response.Status.FORBIDDEN, "<h2>链接已失效</h2>")
        }
        return when {
            session.method == Method.GET && session.uri == "/" ->
                textResponse(Response.Status.OK, formHtml())
            session.method == Method.POST && session.uri == "/submit" -> {
                val files = HashMap<String, String>()
                runCatching { session.parseBody(files) }
                // NanoHTTPD may decode form fields into session.parameters instead of postData,
                // depending on the Content-Type; merge both sources.
                val form = session.parameters.mapValues { (_, values) ->
                    values.firstOrNull().orEmpty()
                } + parseQuery(files["postData"].orEmpty())
                val draft = BridgeDraft(
                    type = form["type"].orEmpty().ifBlank { "WEBDAV" },
                    name = form["name"].orEmpty(),
                    localPath = form["localPath"].orEmpty().ifBlank { "/storage/emulated/0" },
                    url = form["url"].orEmpty(),
                    host = form["host"].orEmpty(),
                    port = form["port"].orEmpty(),
                    share = form["share"].orEmpty(),
                    rootPath = form["rootPath"].orEmpty().ifBlank { "/" },
                    domain = form["domain"].orEmpty(),
                    username = form["username"].orEmpty(),
                    password = form["password"].orEmpty(),
                    secure = form["secure"] == "on" || form["secure"] == "true"
                )
                mainHandler.post { onDraft(draft) }
                textResponse(
                    Response.Status.OK,
                    """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>已提交</title><style>$BASE_STYLE</style></head><body>
                    <main class="card"><div class="ok">✓</div><h1>参数已提交</h1>
                    <p>请回到电视上的 TomatoFile，检查自动回显的参数并点击“保存存储”。</p></main>
                    </body></html>
                    """.trimIndent()
                )
            }
            else -> textResponse(Response.Status.NOT_FOUND, "<h2>Not found</h2>")
        }
    }

    private fun textResponse(status: Response.Status, html: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", html).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }

    private fun formHtml(): String {
        fun option(value: String, label: String, selected: Boolean = false) =
            """<option value="$value"${if (selected) " selected" else ""}>$label</option>"""

        return """
            <!doctype html>
            <html lang="zh-CN"><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>TomatoFile · 添加存储</title>
            <style>$BASE_STYLE</style>
            </head><body>
            <main class="card">
              <div class="brand">🍅 TomatoFile</div>
              <h1>在手机上填写存储参数</h1>
              <p class="hint">提交后会自动回显到电视，确认无误再在电视上保存。</p>
              <form method="post" action="/submit?token=$token">
                <label>存储类型
                  <select name="type" id="type" onchange="updateFields()">
                    ${option("WEBDAV", "WebDAV", true)}
                    ${option("SMB", "SMB")}
                    ${option("FTP", "FTP")}
                    ${option("LOCAL", "本机目录")}
                  </select>
                </label>
                <label>显示名称<input name="name" placeholder="例如：家里的 NAS" required></label>

                <div class="section" data-type="WEBDAV">
                  <label>WebDAV 地址<input name="url" placeholder="https://nas.example.com/dav/"></label>
                </div>

                <div class="section" data-type="SMB FTP">
                  <label>服务器地址<input name="host" placeholder="192.168.1.10"></label>
                  <label>端口<input name="port" inputmode="numeric" placeholder="SMB 445 / FTP 21"></label>
                </div>

                <div class="section" data-type="SMB">
                  <label>共享名<input name="share" placeholder="shared"></label>
                  <label>共享内根目录<input name="rootPath" value="/"></label>
                  <label>域（可选）<input name="domain" placeholder="WORKGROUP"></label>
                </div>

                <div class="section" data-type="FTP">
                  <label>FTP 根目录<input name="rootPath" value="/"></label>
                  <label class="check"><input type="checkbox" name="secure"> 使用 FTPS</label>
                </div>

                <div class="section" data-type="LOCAL">
                  <label>本机目录路径<input name="localPath" value="/storage/emulated/0"></label>
                </div>

                <div class="section" data-type="WEBDAV SMB FTP">
                  <label>用户名<input name="username" autocomplete="username"></label>
                  <label>密码<input name="password" type="password" autocomplete="current-password"></label>
                </div>

                <button type="submit">提交到电视</button>
              </form>
              <p class="foot">仅在同一局域网内可访问；关闭二维码窗口后链接立即失效。</p>
            </main>
            <script>
              function updateFields() {
                const type = document.getElementById('type').value;
                document.querySelectorAll('[data-type]').forEach(function (el) {
                  el.style.display = el.dataset.type.split(' ').includes(type) ? 'block' : 'none';
                });
              }
              updateFields();
            </script>
            </body></html>
        """.trimIndent()
    }

    private fun parseQuery(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val index = pair.indexOf('=')
            val key = if (index >= 0) pair.substring(0, index) else pair
            val value = if (index >= 0) pair.substring(index + 1) else ""
            runCatching {
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }.getOrNull()
        }.toMap()

    companion object {
        private const val BASE_STYLE = """
            :root{color-scheme:light dark;--red:#d9483f;--bg:#faf6f3;--card:#fff;--text:#241d1b;--muted:#776b66}
            *{box-sizing:border-box}body{margin:0;background:linear-gradient(160deg,#fff7f2,#f3e7df);
            color:var(--text);font-family:system-ui,-apple-system,"Segoe UI",sans-serif;min-height:100vh;padding:18px}
            .card{max-width:560px;margin:0 auto;background:var(--card);border-radius:24px;padding:24px;
            box-shadow:0 18px 50px rgba(80,30,20,.12)}.brand{color:var(--red);font-weight:800;letter-spacing:.02em}
            h1{font-size:22px;margin:12px 0 6px}.hint,.foot{color:var(--muted);font-size:13px;line-height:1.5}
            form{display:flex;flex-direction:column;gap:12px;margin-top:18px}
            label{display:flex;flex-direction:column;gap:6px;font-size:13px;font-weight:600}
            input,select{width:100%;border:1px solid #e4d6cf;border-radius:12px;padding:11px 12px;
            font-size:15px;background:#fffdfb;color:var(--text);outline:none}
            input:focus,select:focus{border-color:var(--red);box-shadow:0 0 0 3px rgba(217,72,63,.12)}
            .section{display:flex;flex-direction:column;gap:12px}
            .check{flex-direction:row;align-items:center;gap:8px;font-weight:500}
            .check input{width:auto}button{border:0;border-radius:14px;padding:14px;background:var(--red);
            color:#fff;font-size:16px;font-weight:700;margin-top:6px}button:active{opacity:.85}
            .ok{width:64px;height:64px;border-radius:50%;background:#2e9e62;color:#fff;display:grid;
            place-items:center;font-size:34px;margin:20px auto}
            @media(prefers-color-scheme:dark){:root{--bg:#171210;--card:#221a17;--text:#f8eee9;--muted:#b9a9a1}
            body{background:linear-gradient(160deg,#1b1310,#0f0b09)}input,select{background:#2c221e;border-color:#493a34}}
        """
        private val active = mutableSetOf<SourceBridgeServer>()

        fun start(context: Context, onDraft: (BridgeDraft) -> Unit): SourceBridgeServer? {
            val app = context.applicationContext
            for (port in 8765..8775) {
                try {
                    ServerSocket(port).use { }
                } catch (_: Throwable) {
                    continue
                }
                val server = SourceBridgeServer(port, onDraft)
                return runCatching {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    active += server
                    server
                }.getOrNull()
            }
            return null
        }

        private fun localIpv4Address(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    override fun stop() {
        active.remove(this)
        super.stop()
    }
}
