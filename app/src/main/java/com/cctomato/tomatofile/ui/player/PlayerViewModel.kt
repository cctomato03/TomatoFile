package com.cctomato.tomatofile.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileKinds
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.model.PreviewKind
import com.cctomato.tomatofile.data.model.SourceType
import com.cctomato.tomatofile.data.player.TomatoDataSource
import com.cctomato.tomatofile.data.player.TomatoMedia
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.ui.nav.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val sourceName: String = "",
    val title: String = "",
    val isVideo: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val error: String? = null
)

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = application.tomatoContainer
    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()
    private val path: String = Routes.decodePath(savedStateHandle.get<String>("path"))

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    val player: ExoPlayer? get() = exoPlayer

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    loading = false,
                    buffering = false,
                    playing = false,
                    error = error.localizedMessage ?: error.errorCodeName
                )
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            syncState()
        }
    }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val source = container.sources.get(sourceId)
            if (source == null) {
                _state.value = PlayerUiState(loading = false, error = "存储配置已不存在")
                return@launch
            }
            val name = FsPaths.name(path)
            val entry = runCatching {
                container.repository.list(source, FsPaths.parent(path))
                    .firstOrNull { it.path == path }
            }.getOrNull() ?: FileEntry(
                name = name,
                path = path,
                isDirectory = false,
                mimeType = FileKinds.mimeForName(name)
            )
            if (entry.isDirectory) {
                _state.value = PlayerUiState(loading = false, error = "无法播放一个目录")
                return@launch
            }
            val isVideo = entry.kind == PreviewKind.VIDEO || entry.mimeType.startsWith("video/")
            val mediaItem = try {
                buildMediaItem(source, entry)
            } catch (t: Throwable) {
                _state.value = PlayerUiState(
                    loading = false,
                    sourceName = source.name,
                    title = entry.name,
                    isVideo = isVideo,
                    error = t.message ?: "无法准备媒体"
                )
                return@launch
            }
            val app = getApplication<Application>()
            val dataSourceFactory = DefaultDataSource.Factory(app, TomatoDataSource.Factory(app))
            val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
            val renderers = DefaultRenderersFactory(app).setEnableDecoderFallback(true)
            val exo = ExoPlayer.Builder(app, renderers)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(DefaultTrackSelector(app))
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
            exo.setAudioAttributes(AudioAttributes.DEFAULT, true)
            exo.addListener(listener)
            exo.setMediaItem(mediaItem)
            exo.prepare()
            exo.playWhenReady = true
            exoPlayer = exo
            _state.update {
                it.copy(
                    loading = false,
                    sourceName = source.name,
                    title = entry.name,
                    isVideo = isVideo,
                    buffering = true,
                    error = null
                )
            }
            startProgressTicker()
        }
    }

    private suspend fun buildMediaItem(source: FileSource, entry: FileEntry): MediaItem {
        return if (source.type == SourceType.LOCAL) {
            val file = container.repository.localFile(source, entry)
            MediaItem.Builder()
                .setMediaId("${source.id}:${entry.path}")
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(entry.name)
                        .build()
                )
                .build()
        } else {
            TomatoMedia.mediaItem(source, entry)
        }
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (isActive) {
                val p = exoPlayer ?: break
                if (p.isPlaying || p.playbackState == Player.STATE_READY) syncState()
                delay(500L)
            }
        }
    }

    private fun syncState() {
        val p = exoPlayer ?: return
        _state.update { current ->
            current.copy(
                playing = p.isPlaying,
                buffering = p.playbackState == Player.STATE_BUFFERING,
                durationMs = p.duration.coerceAtLeast(0L),
                positionMs = p.currentPosition.coerceAtLeast(0L),
                bufferedMs = p.bufferedPosition.coerceAtLeast(0L)
            )
        }
    }

    fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
            p.play()
        }
        syncState()
    }

    fun seekBy(deltaMs: Long) {
        val p = exoPlayer ?: return
        val duration = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, duration))
        syncState()
    }

    fun seekTo(positionMs: Long) {
        val p = exoPlayer ?: return
        p.seekTo(positionMs.coerceAtLeast(0L))
        syncState()
    }

    fun pause() {
        exoPlayer?.pause()
        syncState()
    }

    fun retry() {
        val p = exoPlayer ?: return
        _state.update { it.copy(error = null, loading = true) }
        p.prepare()
        p.playWhenReady = true
    }

    override fun onCleared() {
        exoPlayer?.removeListener(listener)
        exoPlayer?.release()
        exoPlayer = null
        super.onCleared()
    }
}