package com.cctomato.tomatofile.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.model.PreviewKind
import com.cctomato.tomatofile.data.model.SourceType
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.net.URLEncoder

data class TrackOption(val id: Int, val name: String)

data class PlayerPlaylistItem(
    val entry: FileEntry,
    val title: String,
    val artist: String
)

data class VlcPlayerUiState(
    val loading: Boolean = true,
    val sourceName: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isVideo: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val error: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioId: Int = -1,
    val selectedSubtitleId: Int = -1,
    val albumArt: Bitmap? = null,
    val playlist: List<PlayerPlaylistItem> = emptyList(),
    val playlistIndex: Int = 0,
    val lyrics: String? = null,
    val lyricsLoading: Boolean = false,
    val lyricsError: String? = null
)

/**
 * libVLC-backed player. Compared with the platform Media3 pipeline this also handles the
 * software-decoded containers (WMV, AVI, RMVB, TS/M2TS, FLV, ...) used by the TV/desktop world.
 */
class VlcPlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()
    private val path: String = Routes.decodePath(savedStateHandle.get<String>("path"))
    private val app = getApplication<Application>()

    private val libVlc = LibVLC(
        app,
        arrayListOf(
            "--audio-time-stretch",
            "--network-caching=1500",
            "--no-video-title-show"
        )
    )

    private val mediaPlayer = MediaPlayer(libVlc).apply {
        setEventListener { event -> onPlayerEvent(event) }
    }

    private val _state = MutableStateFlow(VlcPlayerUiState())
    val state: StateFlow<VlcPlayerUiState> = _state.asStateFlow()

    private var currentSource: FileSource? = null
    private var currentEntry: FileEntry? = null
    private var videoLayout: VLCVideoLayout? = null
    private var infoJob: Job? = null
    private var lyricsJob: Job? = null
    private val http = OkHttpClient()

    init {
        load()
        viewModelScope.launch {
            while (isActive) {
                syncProgress()
                delay(500L)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val source = container.sources.get(sourceId)
            if (source == null) {
                _state.value = VlcPlayerUiState(loading = false, error = "存储配置已不存在")
                return@launch
            }
            currentSource = source
            val entry = runCatching {
                container.repository.list(source, FsPaths.parent(path))
                    .firstOrNull { it.path == path }
            }.getOrNull() ?: FileEntry(
                name = FsPaths.name(path),
                path = path,
                isDirectory = false,
                mimeType = ""
            )
            if (entry.isDirectory) {
                _state.value = VlcPlayerUiState(loading = false, error = "无法播放一个目录")
                return@launch
            }

            val siblings = runCatching {
                container.repository.list(source, FsPaths.parent(path))
            }.getOrDefault(emptyList())
            val isVideo = entry.kind == PreviewKind.VIDEO
            val audioEntries = if (isVideo) emptyList() else siblings
                .filter { it.kind == PreviewKind.AUDIO }
                .sortedWith(compareBy<FileEntry> { it.name.lowercase() })
            val playlistEntries = if (isVideo) listOf(entry) else audioEntries.ifEmpty { listOf(entry) }
            val index = playlistEntries.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
            val playlist = playlistEntries.map {
                PlayerPlaylistItem(
                    entry = it,
                    title = it.name,
                    artist = ""
                )
            }

            currentEntry = entry
            _state.update {
                it.copy(
                    loading = false,
                    sourceName = source.name,
                    title = if (isVideo) entry.name.substringBeforeLast('.') else entry.name,
                    isVideo = isVideo,
                    playlist = playlist,
                    playlistIndex = index,
                    error = null,
                    buffering = true
                )
            }
            playEntry(source, entry, playlistEntries, index)
        }
    }

    private fun playEntry(
        source: FileSource,
        entry: FileEntry,
        entries: List<FileEntry>,
        index: Int
    ) {
        currentSource = source
        currentEntry = entry
        infoJob?.cancel()
        lyricsJob?.cancel()

        val media = try {
            buildMedia(source, entry)
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    loading = false,
                    playing = false,
                    buffering = false,
                    error = t.message ?: "无法准备媒体"
                )
            }
            return
        }

        mediaPlayer.stop()
        mediaPlayer.media = media
        media.release()
        mediaPlayer.rate = _state.value.speed
        mediaPlayer.play()

        _state.update {
            it.copy(
                loading = false,
                title = if (entry.kind == PreviewKind.VIDEO) entry.name.substringBeforeLast('.') else entry.name,
                isVideo = entry.kind == PreviewKind.VIDEO,
                playing = false,
                buffering = true,
                positionMs = 0L,
                durationMs = 0L,
                error = null,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                selectedAudioId = -1,
                selectedSubtitleId = -1,
                albumArt = null,
                artist = "",
                album = "",
                lyrics = null,
                lyricsError = null,
                playlistIndex = index,
                playlist = entries.map {
                    PlayerPlaylistItem(it, it.name, "")
                }
            )
        }

        if (entry.kind != PreviewKind.VIDEO) {
            loadAudioInfo(source, entry)
        }
    }

    private fun buildMedia(source: FileSource, entry: FileEntry): Media {
        val uri = mediaUri(source, entry)
        val media = Media(libVlc, uri)
        media.addOption(":network-caching=1500")
        when (source.type) {
            SourceType.WEBDAV -> {
                if (source.username.isNotBlank()) {
                    media.addOption(":http-user=${source.username}")
                    media.addOption(":http-pwd=${source.password}")
                }
            }
            SourceType.FTP -> {
                if (source.username.isNotBlank()) {
                    media.addOption(":ftp-user=${source.username}")
                    media.addOption(":ftp-pwd=${source.password}")
                }
            }
            SourceType.SMB -> {
                if (source.username.isNotBlank()) {
                    media.addOption(":smb-user=${source.username}")
                    media.addOption(":smb-pwd=${source.password}")
                }
                if (source.domain.isNotBlank()) {
                    media.addOption(":smb-domain=${source.domain}")
                }
            }
            SourceType.LOCAL -> Unit
        }
        return media
    }

    private fun mediaUri(source: FileSource, entry: FileEntry): Uri {
        val path = entry.path
        return when (source.type) {
            SourceType.LOCAL -> Uri.fromFile(
                File(source.localPath, path.trimStart('/'))
            )
            SourceType.WEBDAV -> {
                val base = source.url.trimEnd('/')
                val encoded = path.trimStart('/').split('/').joinToString("/") { Uri.encode(it) }
                Uri.parse("$base/$encoded")
            }
            SourceType.FTP -> {
                val scheme = if (source.secure) "ftps" else "ftp"
                val port = if (source.port > 0) source.port else 21
                Uri.parse("$scheme://${source.host}:$port$path")
            }
            SourceType.SMB -> {
                val port = if (source.port > 0) source.port else 445
                val share = source.share.trim('/')
                Uri.parse("smb://${source.host}:$port/$share$path")
            }
        }
    }

    private fun onPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                _state.update { it.copy(playing = true, buffering = false, error = null) }
                refreshTracks()
            }
            MediaPlayer.Event.Paused -> _state.update { it.copy(playing = false) }
            MediaPlayer.Event.Stopped -> _state.update { it.copy(playing = false) }
            MediaPlayer.Event.Buffering -> {
                _state.update { it.copy(buffering = event.buffering < 100f) }
            }
            MediaPlayer.Event.TimeChanged,
            MediaPlayer.Event.LengthChanged -> syncProgress()
            MediaPlayer.Event.Vout -> refreshTracks()
            MediaPlayer.Event.EndReached -> onEndReached()
            MediaPlayer.Event.EncounteredError -> {
                _state.update {
                    it.copy(
                        loading = false,
                        playing = false,
                        buffering = false,
                        error = "这个媒体无法解码或格式不受支持"
                    )
                }
            }
            else -> Unit
        }
    }

    private fun syncProgress() {
        if (mediaPlayer.isReleased) return
        val duration = mediaPlayer.length.coerceAtLeast(0L)
        val position = mediaPlayer.time.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        _state.update {
            it.copy(
                durationMs = duration,
                positionMs = position,
                playing = mediaPlayer.isPlaying,
                speed = mediaPlayer.rate.takeIf { rate -> rate > 0f } ?: it.speed
            )
        }
    }

    private fun refreshTracks() {
        if (mediaPlayer.isReleased) return
        val audio = mediaPlayer.audioTracks
            ?.map { TrackOption(it.id, it.name.ifBlank { "音轨 ${it.id}" }) }
            ?: emptyList()
        val subs = mediaPlayer.spuTracks
            ?.map { TrackOption(it.id, it.name.ifBlank { "字幕 ${it.id}" }) }
            ?: emptyList()
        _state.update {
            it.copy(
                audioTracks = audio,
                subtitleTracks = subs,
                selectedAudioId = mediaPlayer.audioTrack,
                selectedSubtitleId = mediaPlayer.spuTrack
            )
        }
    }

    private fun onEndReached() {
        val current = _state.value
        if (!current.isVideo && current.playlist.size > 1) {
            val next = (current.playlistIndex + 1) % current.playlist.size
            val item = current.playlist[next]
            val source = currentSource ?: return
            playEntry(source, item.entry, current.playlist.map { it.entry }, next)
        } else {
            _state.update { it.copy(playing = false, buffering = false) }
        }
    }

    private fun loadAudioInfo(source: FileSource, entry: FileEntry) {
        infoJob = viewModelScope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) { container.repository.localFile(source, entry) }
            }.getOrNull()
            var title = entry.name
            var artist = ""
            var album = ""
            var art: Bitmap? = null
            if (file != null) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: title
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
                    val bytes = retriever.embeddedPicture
                    if (bytes != null) {
                        art = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (_: Throwable) {
                    // Keep filename fallbacks.
                } finally {
                    retriever.release()
                }
            }
            _state.update {
                it.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArt = art
                )
            }
            loadLyrics(entry, title, artist)
        }
    }

    private fun loadLyrics(entry: FileEntry, title: String, artist: String) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _state.update { it.copy(lyricsLoading = true, lyricsError = null) }
            val queryTitle = title.ifBlank { entry.name.substringBeforeLast('.') }
            val queryArtist = artist
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    val url = "https://lrclib.net/api/search?track_name=" +
                        URLEncoder.encode(queryTitle, "UTF-8") +
                        "&artist_name=" + URLEncoder.encode(queryArtist, "UTF-8")
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "TomatoFile/1.0 (Android)")
                        .build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        val array = JSONArray(response.body?.string().orEmpty())
                        if (array.length() == 0) return@withContext null
                        val item = array.optJSONObject(0) ?: return@withContext null
                        item.optString("syncedLyrics").ifBlank {
                            item.optString("plainLyrics")
                        }.ifBlank { null }
                    }
                }
            }.getOrNull()
            if (currentEntry?.path != entry.path) return@launch
            _state.update {
                it.copy(
                    lyrics = text,
                    lyricsLoading = false,
                    lyricsError = if (text == null) "没有找到匹配的歌词" else null
                )
            }
        }
    }

    fun attachVideo(layout: VLCVideoLayout) {
        if (videoLayout === layout) return
        runCatching { mediaPlayer.detachViews() }
        videoLayout = layout
        runCatching { mediaPlayer.attachViews(layout, null, true, false) }
    }

    fun detachVideo(layout: VLCVideoLayout? = null) {
        if (layout != null && videoLayout !== layout) return
        runCatching { mediaPlayer.detachViews() }
        videoLayout = null
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        syncProgress()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer.setTime(positionMs.coerceAtLeast(0L))
        syncProgress()
    }

    fun seekBy(deltaMs: Long) {
        val current = mediaPlayer.time
        val duration = mediaPlayer.length
        val target = (current + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        mediaPlayer.setTime(target)
        syncProgress()
    }

    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 3f)
        mediaPlayer.rate = value
        _state.update { it.copy(speed = value) }
    }

    fun selectAudio(id: Int) {
        mediaPlayer.setAudioTrack(id)
        refreshTracks()
    }

    fun selectSubtitle(id: Int) {
        mediaPlayer.setSpuTrack(id)
        refreshTracks()
    }

    fun playPlaylist(index: Int) {
        val current = _state.value
        val item = current.playlist.getOrNull(index) ?: return
        val source = currentSource ?: return
        playEntry(source, item.entry, current.playlist.map { it.entry }, index)
    }

    fun next() {
        val current = _state.value
        if (current.playlist.size <= 1) return
        val next = (current.playlistIndex + 1) % current.playlist.size
        playPlaylist(next)
    }

    fun previous() {
        val current = _state.value
        if (current.playlist.size <= 1) return
        val prev = (current.playlistIndex - 1 + current.playlist.size) % current.playlist.size
        playPlaylist(prev)
    }

    fun retry() = load()

    fun pause() {
        runCatching { mediaPlayer.pause() }
    }

    override fun onCleared() {
        infoJob?.cancel()
        lyricsJob?.cancel()
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { libVlc.release() }
        super.onCleared()
    }
}
