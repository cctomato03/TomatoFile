package com.cctomato.tomatofile.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.ImageMetadata
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.ErrorState
import com.cctomato.tomatofile.ui.components.GradientButton
import com.cctomato.tomatofile.ui.components.InfoRow
import com.cctomato.tomatofile.ui.components.ScreenTopBar
import com.cctomato.tomatofile.util.AuthorLink
import com.cctomato.tomatofile.util.ExternalIntents
import com.cctomato.tomatofile.util.findActivity
import com.cctomato.tomatofile.util.formatBytes
import com.cctomato.tomatofile.util.formatDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ImageViewerScreen(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = oldStatus ?: false
            controller?.isAppearanceLightNavigationBars = oldNav ?: false
        }
    }

    when (val current = state) {
        ViewerUiState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
        }
        is ViewerUiState.Error -> AppBackground {
            Column(Modifier.fillMaxSize()) {
                ScreenTopBar(title = "图片", onBack = onBack)
                ErrorState(current.message, onRetry = { viewModel.retry() })
            }
        }
        is ViewerUiState.Ready -> ViewerContent(
            state = current,
            onBack = onBack,
            viewModel = viewModel
        )
    }
}

@Composable
private fun ViewerContent(
    state: ViewerUiState.Ready,
    onBack: () -> Unit,
    viewModel: ViewerViewModel
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = state.startIndex.coerceIn(0, state.images.lastIndex),
        pageCount = { state.images.size }
    )
    var zoomed by remember { mutableStateOf(false) }
    var linkReady by remember { mutableStateOf(false) }
    var linkLoading by remember { mutableStateOf(false) }
    var loadedPages by remember { mutableStateOf(setOf<Int>()) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var metadataLoading by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var metadataJob by remember { mutableStateOf<Job?>(null) }

    val currentEntry = state.images.getOrNull(pagerState.currentPage) ?: state.images.first()

    LaunchedEffect(pagerState.currentPage) {
        zoomed = false
        metadata = null
        metadataLoading = false
        linkLoading = false
        metadataJob?.cancel()
        linkReady = loadedPages.contains(pagerState.currentPage)
    }

    fun loadMetadata(openSheet: Boolean) {
        showMetadata = openSheet
        if (metadata != null) return
        if (metadataLoading) return
        val target = currentEntry
        metadataJob?.cancel()
        metadataJob = scope.launch {
            metadataLoading = true
            val result = runCatching { viewModel.metadataFor(target) }.getOrNull()
            metadataLoading = false
            if (currentEntry.path == target.path) {
                metadata = result
                if (result == null) {
                    snackbar.showSnackbar("无法读取这张图片的元数据")
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || zoomed) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (pagerState.currentPage < state.images.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { state.images[it].path },
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1
        ) { page ->
            ViewerPage(
                entry = state.images[page],
                viewModel = viewModel,
                onLoaded = {
                    loadedPages = loadedPages + page
                    if (page == pagerState.currentPage) linkReady = true
                },
                onZoomChanged = { isZoomed ->
                    if (page == pagerState.currentPage) zoomed = isZoomed
                }
            )
        }

        // 顶部渐变栏
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    currentEntry.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${pagerState.currentPage + 1} / ${state.images.size}",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            IconButton(onClick = { loadMetadata(openSheet = true) }) {
                Icon(Icons.Rounded.Info, contentDescription = "图片元数据", tint = Color.White)
            }
        }

        // 链接按钮：图片成功加载后才出现，位于右下角。
        AnimatedVisibility(
            visible = linkReady,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 104.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.32f), CircleShape)
                    .clickable(enabled = !linkLoading) {
                        metadataJob?.cancel()
                        metadataJob = scope.launch {
                            linkLoading = true
                            val result = runCatching { viewModel.metadataFor(currentEntry) }.getOrNull()
                            linkLoading = false
                            if (result == null) {
                                snackbar.showSnackbar("无法读取这张图片的元数据")
                                return@launch
                            }
                            metadata = result
                            val link = AuthorLink.fromMetadata(result)
                            val author = result.artist ?: result.copyright
                            if (link == null) {
                                snackbar.showSnackbar(
                                    if (!author.isNullOrBlank()) {
                                        "作者信息不是带协议的链接"
                                    } else {
                                        "这张图片没有作者信息"
                                    }
                                )
                            } else {
                                val opened = ExternalIntents.openUrl(context, link)
                                snackbar.showSnackbar(
                                    if (opened) {
                                        "已交给系统打开：${author ?: link}"
                                    } else {
                                        "没有应用可以响应此链接"
                                    }
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (linkLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = "打开图片作者链接",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // 底部页码
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
                .navigationBarsPadding()
                .padding(top = 32.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${pagerState.currentPage + 1} / ${state.images.size}",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "双指缩放 · 双击放大 · 左右滑动",
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelMedium
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp, start = 20.dp, end = 20.dp)
        )
    }

    if (showMetadata) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMetadata = false },
            sheetState = sheet
        ) {
            MetadataSheet(
                entry = currentEntry,
                metadata = metadata,
                loading = metadataLoading,
                onOpenAuthor = { link ->
                    ExternalIntents.openUrl(context, link)
                }
            )
        }
    }
}

@Composable
private fun ViewerPage(
    entry: FileEntry,
    viewModel: ViewerViewModel,
    onLoaded: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var attempt by remember(entry.path) { mutableIntStateOf(0) }
    var result by remember(entry.path, attempt) { mutableStateOf<Result<File>?>(null) }
    LaunchedEffect(entry.path, attempt) {
        result = runCatching { viewModel.fileFor(entry) }
    }
    val file = result?.getOrNull()
    var decodeFailed by remember(entry.path, attempt) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            file != null -> {
                ZoomableImage(
                    modifier = Modifier.fillMaxSize(),
                    onZoomChanged = onZoomChanged
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            decodeFailed = false
                            onLoaded()
                        },
                        onError = { decodeFailed = true }
                    )
                }
                if (decodeFailed) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("图片解码失败", color = Color.White)
                        TextButton(onClick = { attempt++ }) {
                            Text("重试", color = Color.White)
                        }
                    }
                }
            }
            result == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    result?.exceptionOrNull()?.message ?: "图片加载失败",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
                TextButton(onClick = { attempt++ }) {
                    Text("重试", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MetadataSheet(
    entry: FileEntry,
    metadata: ImageMetadata?,
    loading: Boolean,
    onOpenAuthor: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Text("图片信息", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在读取 EXIF / XMP…", style = MaterialTheme.typography.bodyMedium)
            }
            metadata == null -> Text(
                "这张图片没有可读取的元数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                val width = metadata.width.takeIf { it > 0 }?.toString() ?: "—"
                val height = metadata.height.takeIf { it > 0 }?.toString() ?: "—"
                InfoRow("分辨率", "$width × $height")
                InfoRow("格式", metadata.mimeType)
                InfoRow("大小", formatBytes(entry.size))
                InfoRow("修改时间", formatDate(entry.lastModified))
                InfoRow("作者", metadata.artist.orEmpty())
                InfoRow("版权", metadata.copyright.orEmpty())
                InfoRow("描述", metadata.description.orEmpty())
                InfoRow("相机", metadata.camera.orEmpty())
                InfoRow("拍摄时间", metadata.dateTime.orEmpty())
                InfoRow("位置", metadata.location.orEmpty())
                InfoRow("软件", metadata.software.orEmpty())
                val link = AuthorLink.fromMetadata(metadata)
                if (link != null) {
                    Spacer(Modifier.height(16.dp))
                    GradientButton(
                        text = "打开作者链接",
                        onClick = { onOpenAuthor(link) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Link
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        link,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}