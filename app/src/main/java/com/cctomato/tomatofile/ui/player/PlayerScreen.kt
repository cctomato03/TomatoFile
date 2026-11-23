package com.cctomato.tomatofile.ui.player

import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cctomato.tomatofile.ui.components.GradientButton
import com.cctomato.tomatofile.ui.theme.TomatoAccents
import com.cctomato.tomatofile.ui.tv.LocalTvMode
import com.cctomato.tomatofile.ui.tv.tvFocus
import com.cctomato.tomatofile.util.findActivity
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = viewModel.player
    val tv = LocalTvMode.current
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val playFocus = remember { FocusRequester() }

    fun showControls() {
        controlsVisible = true
        interaction++
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousOrientation = activity?.requestedOrientation
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (activity != null) {
                activity.requestedOrientation =
                    previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(state.isVideo, tv) {
        if (!tv && state.isVideo) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    BackHandler { onBack() }

    LaunchedEffect(controlsVisible, interaction, state.playing, state.isVideo) {
        if (controlsVisible && state.playing && state.isVideo) {
            delay(4000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, tv) {
        if (controlsVisible && tv) {
            delay(80L)
            runCatching { playFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        viewModel.togglePlayPause()
                        showControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        viewModel.seekBy(10_000L)
                        showControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        viewModel.seekBy(-10_000L)
                        showControls()
                        true
                    }
                    else -> when (event.key) {
                        Key.DirectionLeft -> {
                            viewModel.seekBy(-10_000L)
                            showControls()
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.seekBy(10_000L)
                            showControls()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            showControls()
                            false
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (!controlsVisible) {
                                showControls()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    if (state.error == null && !state.loading) {
                        controlsVisible = !controlsVisible
                        interaction++
                    }
                }
            }
    ) {
        if (state.isVideo && player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        setUseController(false)
                        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { playerView -> playerView.player = player },
                onRelease = { playerView -> playerView.player = null },
                modifier = Modifier.fillMaxSize()
            )
        } else if (!state.isVideo && !state.loading) {
            AudioBackdrop(state)
        }

        if (state.loading) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    "正在准备播放…",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.buffering && !state.loading && state.error == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        state.error?.let { error ->
            PlaybackError(
                message = error,
                onRetry = { viewModel.retry() },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = (controlsVisible || !state.isVideo) && !state.loading && state.error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                PlayerTopBar(
                    title = state.title.ifBlank { "正在播放" },
                    subtitle = state.sourceName,
                    isVideo = state.isVideo,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                PlayerCenterControls(
                    playing = state.playing,
                    tv = tv,
                    playFocus = playFocus,
                    onPlayPause = {
                        viewModel.togglePlayPause()
                        showControls()
                    },
                    onRewind = {
                        viewModel.seekBy(-10_000L)
                        showControls()
                    },
                    onForward = {
                        viewModel.seekBy(10_000L)
                        showControls()
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
                PlayerBottomControls(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    bufferedMs = state.bufferedMs,
                    sliderPosition = sliderPosition,
                    onSliderPosition = {
                        sliderPosition = it
                        showControls()
                    },
                    onSeekFinished = {
                        sliderPosition?.let { viewModel.seekTo(it.toLong()) }
                        sliderPosition = null
                        showControls()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    subtitle: String,
    isVideo: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
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
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (isVideo) "视频 · $subtitle" else "音频 · $subtitle",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            if (isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.Audiotrack,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun PlayerCenterControls(
    playing: Boolean,
    tv: Boolean,
    playFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (tv) 34.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerControlButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = "后退 10 秒",
            size = if (tv) 66.dp else 54.dp,
            onClick = onRewind
        )
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .focusRequester(playFocus)
                .size(if (tv) 88.dp else 72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .tvFocus(CircleShape)
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(if (tv) 44.dp else 38.dp)
            )
        }
        PlayerControlButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = "前进 10 秒",
            size = if (tv) 66.dp else 54.dp,
            onClick = onForward
        )
    }
}

@Composable
private fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .tvFocus(CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.42f)
        )
    }
}

@Composable
private fun PlayerBottomControls(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    sliderPosition: Float?,
    onSliderPosition: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = durationMs.coerceAtLeast(0L)
    val shownPosition: Long = sliderPosition?.toLong()
        ?: positionMs.coerceIn(0L, maxOf(duration, 0L))
    val bufferedRatio = if (duration > 0L) {
        (bufferedMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                )
            )
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 34.dp, bottom = 12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bufferedRatio)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.42f))
            )
        }
        Slider(
            value = if (duration > 0L) shownPosition.toFloat() else 0f,
            onValueChange = onSliderPosition,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..maxOf(duration.toFloat(), 1f),
            enabled = duration > 0L,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                disabledThumbColor = Color.White.copy(alpha = 0.4f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.2f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.tvFocus(RoundedCornerShape(12.dp))
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(shownPosition),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(duration),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun AudioBackdrop(state: PlayerUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B1210), Color(0xFF120C0B), Color.Black)
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(42.dp))
                .background(TomatoAccents.Local.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Audiotrack,
                contentDescription = null,
                tint = TomatoAccents.Local,
                modifier = Modifier.size(74.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            state.title.ifBlank { "音频" },
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            state.sourceName,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PlaybackError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFFFB4A6),
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("无法播放", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(20.dp))
        GradientButton(
            text = "重试",
            onClick = onRetry,
            modifier = Modifier.width(190.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("返回", color = Color.White)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}