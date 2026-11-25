package com.cctomato.tomatofile.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cctomato.tomatofile.ui.components.GradientButton
import com.cctomato.tomatofile.ui.theme.TomatoAccents
import com.cctomato.tomatofile.ui.tv.tvFocus
import com.cctomato.tomatofile.util.findActivity
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale
import kotlin.math.abs

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

@Composable
fun VlcPlayerScreen(
    onBack: () -> Unit,
    mediaType: String = "auto",
    viewModel: VlcPlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val videoMode = when (mediaType) {
        "video" -> true
        "audio" -> false
        else -> state.isVideo
    }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var controlsVisible by remember { mutableStateOf(true) }
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragAmount by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            viewModel.pause()
        }
    }

    LaunchedEffect(videoMode, activity) {
        if (videoMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(controlsVisible, state.playing, videoMode) {
        if (controlsVisible && state.playing && videoMode) {
            delay(4000L)
            controlsVisible = false
        }
    }

    BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                }
            }
            .pointerInput(state.durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragAmount = 0f
                        controlsVisible = true
                    },
                    onHorizontalDrag = { _, amount ->
                        dragAmount += amount
                    },
                    onDragEnd = {
                        if (abs(dragAmount) > 16f && state.durationMs > 0L) {
                            viewModel.seekBy((dragAmount / widthPx * state.durationMs).toLong())
                        }
                        dragging = false
                        dragAmount = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragAmount = 0f
                    }
                )
            }
    ) {
        if (videoMode) {
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { viewModel.attachVideo(it) }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { viewModel.detachVideo(it) }
            )
        } else {
            AudioBackdrop(state)
        }

        if (dragging) {
            val delta = if (state.durationMs > 0) {
                (dragAmount / widthPx * state.durationMs).toLong()
            } else 0L
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    if (delta >= 0) "快进 ${formatMs(abs(delta))}" else "快退 ${formatMs(abs(delta))}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        when {
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
            state.error != null -> ErrorPane(
                message = state.error ?: "",
                onRetry = { viewModel.retry() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || !videoMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                PlayerTopBar(
                    state = state,
                    videoMode = videoMode,
                    onBack = onBack,
                    onSpeed = { showSpeed = true },
                    onAudio = { showAudio = true },
                    onSubtitle = { showSubtitle = true }
                )

                if (videoMode) {
                    Row(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundControl(Icons.Rounded.Replay10, "快退 10 秒", 58) {
                            viewModel.seekBy(-10_000L)
                        }
                        RoundControl(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.playing) "暂停" else "播放",
                            82
                        ) {
                            controlsVisible = true
                            viewModel.togglePlayPause()
                        }
                        RoundControl(Icons.Rounded.Forward10, "快进 10 秒", 58) {
                            viewModel.seekBy(10_000L)
                        }
                    }
                }

                if (videoMode) {
                    PlayerBottomBar(
                        state = state,
                        sliderPosition = sliderPosition,
                        onSliderPosition = {
                            sliderPosition = it
                            controlsVisible = true
                        },
                        onSeekFinished = {
                            sliderPosition?.let { viewModel.seekTo(it.toLong()) }
                            sliderPosition = null
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    AudioBottomBar(
                        state = state,
                        sliderPosition = sliderPosition,
                        onSliderPosition = {
                            sliderPosition = it
                            controlsVisible = true
                        },
                        onSeekFinished = {
                            sliderPosition?.let { viewModel.seekTo(it.toLong()) }
                            sliderPosition = null
                        },
                        onSpeed = { showSpeed = true },
                        onPlaylist = { showPlaylist = true },
                        onLyrics = { showLyrics = true },
                        onPrev = { viewModel.previous() },
                        onToggle = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showSpeed) {
        ChoiceMenu(
            title = "播放速度",
            options = SPEEDS.map { speed -> speed to formatSpeed(speed) },
            selected = state.speed,
            onDismiss = { showSpeed = false },
            onSelect = {
                viewModel.setSpeed(it)
                showSpeed = false
            }
        )
    }
    if (showAudio) {
        TrackMenu(
            title = "音轨",
            tracks = state.audioTracks,
            selected = state.selectedAudioId,
            onDismiss = { showAudio = false },
            onSelect = {
                viewModel.selectAudio(it)
                showAudio = false
            }
        )
    }
    if (showSubtitle) {
        TrackMenu(
            title = "字幕",
            tracks = listOf(TrackOption(-1, "关闭字幕")) + state.subtitleTracks,
            selected = state.selectedSubtitleId,
            onDismiss = { showSubtitle = false },
            onSelect = {
                viewModel.selectSubtitle(it)
                showSubtitle = false
            }
        )
    }
    if (showPlaylist) {
        ModalBottomSheet(onDismissRequest = { showPlaylist = false }) {
            PlaylistSheet(
                state = state,
                onSelect = {
                    viewModel.playPlaylist(it)
                    showPlaylist = false
                }
            )
        }
    }
    if (showLyrics) {
        ModalBottomSheet(onDismissRequest = { showLyrics = false }) {
            LyricsSheet(state)
        }
    }
}

@Composable
private fun AudioBackdrop(state: VlcPlayerUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF231312), Color(0xFF090909), Color.Black)
                )
            )
    ) {
        state.albumArt?.let { art ->
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentScale = ContentScale.Crop,
                alpha = 0.28f
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.86f))
                        )
                    )
            )
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(248.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(TomatoAccents.Local.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (state.albumArt != null) {
                    Image(
                        bitmap = state.albumArt.asImageBitmap(),
                        contentDescription = "专辑封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(92.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                state.title.ifBlank { "未知曲目" },
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOf(state.artist, state.album).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { state.sourceName },
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    state: VlcPlayerUiState,
    videoMode: Boolean,
    onBack: () -> Unit,
    onSpeed: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.tvFocus(CircleShape)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                state.title.ifBlank { "正在播放" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(state.artist, state.sourceName).filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        TopChip(formatSpeed(state.speed), onSpeed)
        if (videoMode) {
            TopChip("音轨", onAudio)
            TopChip("字幕", onSubtitle)
        }
    }
}

@Composable
private fun TopChip(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AudioBottomBar(
    state: VlcPlayerUiState,
    sliderPosition: Float?,
    onSliderPosition: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onSpeed: () -> Unit,
    onPlaylist: () -> Unit,
    onLyrics: () -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = state.durationMs
    val shown = sliderPosition ?: state.positionMs.toFloat()
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = shown.coerceIn(0f, maxOf(duration.toFloat(), 1f)),
            onValueChange = onSliderPosition,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..maxOf(duration.toFloat(), 1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatMs(shown.toLong()),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatMs(duration),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundControl(Icons.Rounded.SkipPrevious, "上一首", 52, onPrev)
            RoundControl(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.playing) "暂停" else "播放",
                72,
                onToggle
            )
            RoundControl(Icons.Rounded.SkipNext, "下一首", 52, onNext)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopChip("倍速 ${formatSpeed(state.speed)}", onSpeed)
            TopChip("列表", onPlaylist)
            TopChip("歌词", onLyrics)
        }
    }
}

@Composable
private fun PlayerBottomBar(
    state: VlcPlayerUiState,
    sliderPosition: Float?,
    onSliderPosition: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = state.durationMs
    val shown = sliderPosition ?: state.positionMs.toFloat()
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Slider(
            value = shown.coerceIn(0f, maxOf(duration.toFloat(), 1f)),
            onValueChange = onSliderPosition,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..maxOf(duration.toFloat(), 1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatMs(shown.toLong()),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatMs(duration),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Int,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (size > 70) 0.26f else 0.16f))
            .tvFocus(CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size((size * 0.48f).dp)
        )
    }
}

@Composable
private fun ChoiceMenu(
    title: String,
    options: List<Pair<Float, String>>,
    selected: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    Box {
        DropdownMenu(expanded = expanded, onDismissRequest = {
            expanded = false
            onDismiss()
        }) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (abs(value - selected) < 0.01f) "$label  ✓" else label
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackMenu(
    title: String,
    tracks: List<TrackOption>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    Box {
        DropdownMenu(expanded = expanded, onDismissRequest = {
            expanded = false
            onDismiss()
        }) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            if (tracks.isEmpty()) {
                DropdownMenuItem(text = { Text("没有可选轨道") }, onClick = {})
            }
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (track.id == selected) "✓ " else "") + track.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(track.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistSheet(state: VlcPlayerUiState, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Text(
            "播放列表 · ${state.playlist.size} 首",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(state.playlist) { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .background(
                            if (index == state.playlistIndex) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (index == state.playlistIndex) {
                            MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (index == state.playlistIndex) {
                        Text("正在播放", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSheet(state: VlcPlayerUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text("歌词", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        when {
            state.lyricsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在匹配歌词…")
            }
            state.lyrics != null -> Text(
                state.lyrics,
                modifier = Modifier
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge
            )
            else -> Text(
                state.lyricsError ?: "暂无歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("播放失败", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        GradientButton(text = "重试", onClick = onRetry)
    }
}

private fun formatSpeed(speed: Float): String = String.format(Locale.US, "%.2fx", speed)

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
