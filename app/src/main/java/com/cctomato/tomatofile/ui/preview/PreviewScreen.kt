package com.cctomato.tomatofile.ui.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cctomato.tomatofile.data.model.PreviewKind
import com.cctomato.tomatofile.data.preview.PdfPageRenderer
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.EmptyState
import com.cctomato.tomatofile.ui.components.ErrorState
import com.cctomato.tomatofile.ui.components.FileTypeBadge
import com.cctomato.tomatofile.ui.components.GradientButton
import com.cctomato.tomatofile.ui.components.InfoRow
import com.cctomato.tomatofile.ui.components.LoadingPane
import com.cctomato.tomatofile.ui.components.RoundIconButton
import com.cctomato.tomatofile.ui.components.ScreenTopBar
import com.cctomato.tomatofile.ui.components.kindIcon
import com.cctomato.tomatofile.ui.theme.TomatoAccents
import com.cctomato.tomatofile.ui.tv.tvScrollKeys
import com.cctomato.tomatofile.util.ExternalIntents
import com.cctomato.tomatofile.util.formatBytes
import com.cctomato.tomatofile.util.formatDate
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }

    val text = (state.content as? PreviewContent.TextContentUi)?.content
    val file = when (val content = state.content) {
        is PreviewContent.TextContentUi -> content.file
        is PreviewContent.PdfFile -> content.file
        is PreviewContent.OfficeText -> content.file
        is PreviewContent.External -> content.file
        null -> null
    }

    AppBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ScreenTopBar(
                    title = state.entry?.name ?: "预览",
                    subtitle = state.sourceName,
                    onBack = onBack
                ) {
                    if (text != null) {
                        RoundIconButton(
                            icon = Icons.Rounded.ContentCopy,
                            contentDescription = "复制全部",
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText(text.text, text.text)
                                )
                                scope.launch { snackbar.showSnackbar("已复制到剪贴板") }
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (file != null) {
                        RoundIconButton(
                            icon = Icons.Rounded.OpenInNew,
                            contentDescription = "用其他应用打开",
                            onClick = {
                                val opened = ExternalIntents.openFile(
                                    context,
                                    file,
                                    state.entry?.mimeType.orEmpty()
                                )
                                if (!opened) {
                                    scope.launch { snackbar.showSnackbar("没有可以打开此文件的应用") }
                                }
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    RoundIconButton(
                        icon = Icons.Rounded.Info,
                        contentDescription = "文件信息",
                        onClick = { showInfo = true }
                    )
                }

                Box(Modifier.weight(1f)) {
                    when {
                        state.loading -> LoadingPane("正在准备预览…")
                        state.error != null -> ErrorState(
                            state.error!!,
                            onRetry = { viewModel.retry() }
                        )
                        state.content is PreviewContent.TextContentUi -> TextPreview(
                            content = (state.content as PreviewContent.TextContentUi).content,
                            truncatedMessage = "文件较大，仅预览前 2 MB"
                        )
                        state.content is PreviewContent.OfficeText -> {
                            val office = state.content as PreviewContent.OfficeText
                            OfficeTextPreview(text = office.text, kind = office.kind)
                        }
                        state.content is PreviewContent.PdfFile -> {
                            val pdf = state.content as PreviewContent.PdfFile
                            PdfPreview(file = pdf.file, expectedPages = pdf.pageCount)
                        }
                        state.content is PreviewContent.External -> {
                            val external = state.content as PreviewContent.External
                            ExternalPreview(
                                file = external.file,
                                mimeType = external.mimeType,
                                message = external.message,
                                entryName = state.entry?.name ?: external.file.name,
                                snackbar = snackbar
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(state.entry?.name ?: "文件信息") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InfoRow("路径", state.entry?.path.orEmpty())
                    InfoRow("大小", state.entry?.let { formatBytes(it.size) }.orEmpty())
                    InfoRow("修改时间", state.entry?.let { formatDate(it.lastModified) }.orEmpty())
                    InfoRow("类型", state.entry?.mimeType.orEmpty())
                    InfoRow("来源", state.sourceName)
                    state.entry?.let { entry ->
                        InfoRow("扩展名", entry.extension)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun TextPreview(content: com.cctomato.tomatofile.data.preview.TextContent, truncatedMessage: String) {
    val scrollState = rememberLazyListState()
    if (content.binary) {
        EmptyState(
            title = "无法以文本预览",
            message = content.text,
            icon = Icons.Rounded.ErrorOutline
        )
        return
    }
    val lines = remember(content.text) { content.text.split('\n') }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (content.truncated || content.charset != "UTF-8") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    buildString {
                        if (content.truncated) append(truncatedMessage)
                        if (content.charset != "UTF-8") {
                            if (isNotEmpty()) append(" · ")
                            append("编码 ${content.charset}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .tvScrollKeys(scrollState),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            items(lines.size) { index ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(42.dp)
                            .padding(top = 2.dp, end = 8.dp)
                    )
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            if (lines[index].isEmpty()) " " else lines[index],
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficeTextPreview(text: String, kind: PreviewKind) {
    val lines = remember(text) { text.split('\n') }
    val scrollState = rememberLazyListState()
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TomatoAccents.forKind(kind).copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeBadge(kind, size = 32.dp, iconSize = 17.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                "已从文档中提取文本，复杂排版、图片与图表不会显示",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .tvScrollKeys(scrollState),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
        ) {
            items(lines.size) { index ->
                val line = lines[index]
                if (line.isBlank()) {
                    Spacer(Modifier.height(7.dp))
                } else {
                    SelectionContainer {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreview(file: File, expectedPages: Int) {
    val result = remember(file) { runCatching { PdfPageRenderer(file) } }
    val scrollState = rememberLazyListState()
    val renderer = result.getOrNull()
    DisposableEffect(renderer) {
        onDispose { renderer?.close() }
    }
    if (renderer == null) {
        ErrorState("PDF 渲染器初始化失败，文件可能已加密或损坏")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .tvScrollKeys(scrollState),
        state = scrollState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(renderer.pageCount) { index ->
            PdfPage(renderer = renderer, index = index)
        }
        item {
            Text(
                "共 ${renderer.pageCount} 页",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfPageRenderer, index: Int) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .border(1.dp, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
    ) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxWidth.toPx().toInt()
        }
        var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
        var failed by remember(index, widthPx) { mutableStateOf(false) }

        LaunchedEffect(index, widthPx) {
            runCatching { renderer.render(index, widthPx) }
                .onSuccess { bitmap = it }
                .onFailure { failed = true }
        }

        val loaded = bitmap
        when {
            loaded != null -> {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = "第 ${index + 1} 页",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(loaded.width.toFloat() / loaded.height.toFloat())
                )
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            failed -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("第 ${index + 1} 页渲染失败", color = androidx.compose.ui.graphics.Color(0xFF777777))
            }
            else -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ExternalPreview(
    file: File,
    mimeType: String,
    message: String,
    entryName: String,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FileTypeBadge(
                com.cctomato.tomatofile.data.model.FileKinds.previewKind(entryName, mimeType),
                size = 84.dp,
                iconSize = 40.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                entryName,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatBytes(file.length())} · ${mimeType}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            GradientButton(
                text = "用其他应用打开",
                onClick = {
                    if (!ExternalIntents.openFile(context, file, mimeType)) {
                        scope.launch { snackbar.showSnackbar("没有可以打开此文件的应用") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.OpenInNew
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    if (!ExternalIntents.shareFile(context, file, mimeType)) {
                        scope.launch { snackbar.showSnackbar("无法分享此文件") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("分享文件")
            }
        }
    }
}