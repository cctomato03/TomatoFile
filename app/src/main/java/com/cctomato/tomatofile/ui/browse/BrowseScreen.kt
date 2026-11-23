package com.cctomato.tomatofile.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cctomato.tomatofile.data.model.FileEntry
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.FsPaths
import com.cctomato.tomatofile.data.model.PreviewKind
import com.cctomato.tomatofile.data.model.SortMode
import com.cctomato.tomatofile.data.model.ViewMode
import com.cctomato.tomatofile.tomatoContainer
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.EmptyState
import com.cctomato.tomatofile.ui.components.ErrorState
import com.cctomato.tomatofile.ui.components.FileTypeBadge
import com.cctomato.tomatofile.ui.components.LoadingPane
import com.cctomato.tomatofile.ui.components.RoundIconButton
import com.cctomato.tomatofile.ui.components.ScreenTopBar
import com.cctomato.tomatofile.ui.theme.TomatoAccents
import com.cctomato.tomatofile.ui.tv.LocalTvMode
import com.cctomato.tomatofile.ui.tv.tvFocus
import com.cctomato.tomatofile.util.NaturalOrder
import com.cctomato.tomatofile.util.StoragePermissions
import com.cctomato.tomatofile.util.formatBytes
import com.cctomato.tomatofile.util.formatDate
import java.io.File

@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    onOpenPreview: (sourceId: String, path: String) -> Unit,
    onOpenViewer: (sourceId: String, path: String) -> Unit,
    onOpenPlayer: (sourceId: String, path: String) -> Unit,
    viewModel: BrowseViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = context.tomatoContainer.settings
    val sortMode by settings.sortMode.collectAsStateWithLifecycle()
    val viewMode by settings.viewMode.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(StoragePermissions.hasAllFilesAccess(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = StoragePermissions.hasAllFilesAccess(context)
    }

    BackHandler(enabled = !FsPaths.isRoot(state.path)) {
        viewModel.goUp()
    }

    val entries = remember(state.entries, sortMode) {
        sortEntries(state.entries, sortMode)
    }
    val source = state.source
    // Keep one scroll state per directory. Returning to a cached parent therefore restores
    // both the file list and the exact scroll offset.
    val listStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val gridStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val listState = listStates.getOrPut(state.path) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val gridState = gridStates.getOrPut(state.path) {
        androidx.compose.foundation.lazy.grid.LazyGridState()
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = source?.name ?: "正在打开…",
                subtitle = state.path.takeIf { it != "/" } ?: source?.type?.label,
                onBack = {
                    if (!FsPaths.isRoot(state.path)) viewModel.goUp() else onBack()
                }
            ) {
                RoundIconButton(
                    icon = if (viewMode == ViewMode.LIST) Icons.Rounded.GridView else Icons.Rounded.ViewList,
                    contentDescription = "切换视图",
                    onClick = {
                        settings.setViewMode(
                            if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        )
                    }
                )
                Spacer(Modifier.width(6.dp))
                Box {
                    RoundIconButton(
                        icon = Icons.Rounded.Sort,
                        contentDescription = "排序",
                        onClick = { sortMenu = true }
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                trailingIcon = {
                                    if (mode == sortMode) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    settings.setSortMode(mode)
                                    sortMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .tvFocus(CircleShape)
                        .clickable { viewModel.refresh() },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }

            Breadcrumbs(
                source = source,
                path = state.path,
                onNavigate = { viewModel.navigateTo(it) }
            )

            if (state.refreshing && entries.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (source?.type == com.cctomato.tomatofile.data.model.SourceType.LOCAL &&
                !hasPermission
            ) {
                PermissionNotice {
                    StoragePermissions.requestAllFilesAccess(context)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.loading && entries.isEmpty() -> LoadingPane("正在读取目录…")
                    state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.retry() })
                    entries.isEmpty() -> EmptyState(
                        title = "这个目录是空的",
                        message = if (source != null) "在「${source.name}」中没有找到文件" else "没有可显示的文件",
                        icon = Icons.Rounded.Folder,
                        actionLabel = "刷新",
                        onAction = { viewModel.refresh() }
                    )
                    viewMode == ViewMode.LIST -> FileList(
                        entries = entries,
                        source = source,
                        listState = listState,
                        onEntryClick = { entry ->
                            handleEntryClick(
                                source, entry, viewModel,
                                onOpenPreview, onOpenViewer, onOpenPlayer
                            )
                        }
                    )
                    else -> FileGrid(
                        entries = entries,
                        source = source,
                        gridState = gridState,
                        onEntryClick = { entry ->
                            handleEntryClick(
                                source, entry, viewModel,
                                onOpenPreview, onOpenViewer, onOpenPlayer
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun handleEntryClick(
    source: FileSource?,
    entry: FileEntry,
    viewModel: BrowseViewModel,
    onOpenPreview: (String, String) -> Unit,
    onOpenViewer: (String, String) -> Unit,
    onOpenPlayer: (String, String) -> Unit
) {
    if (source == null) return
    when {
        entry.isDirectory -> viewModel.open(entry.path)
        entry.kind == PreviewKind.IMAGE -> onOpenViewer(source.id, entry.path)
        entry.kind == PreviewKind.AUDIO || entry.kind == PreviewKind.VIDEO ->
            onOpenPlayer(source.id, entry.path)
        else -> onOpenPreview(source.id, entry.path)
    }
}

@Composable
private fun Breadcrumbs(source: FileSource?, path: String, onNavigate: (String) -> Unit) {
    val segments = FsPaths.segments(path)
    val accent = source?.let { TomatoAccents.forSource(it.type) } ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BreadcrumbChip(
            text = source?.name ?: "根目录",
            accent = accent,
            active = segments.isEmpty(),
            onClick = { onNavigate("/") }
        )
        var accumulated = ""
        segments.forEach { segment ->
            accumulated += "/$segment"
            val target = accumulated
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(15.dp)
            )
            BreadcrumbChip(
                text = segment,
                accent = accent,
                active = target == path,
                onClick = { onNavigate(target) }
            )
        }
    }
}

@Composable
private fun BreadcrumbChip(text: String, accent: Color, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        maxLines = 1,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Color.White else accent,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent else accent.copy(alpha = 0.10f))
            .tvFocus(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun PermissionNotice(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .tvFocus(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "文件访问权限受限",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "部分目录可能无法读取，点此授权",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun FileList(
    entries: List<FileEntry>,
    source: FileSource?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEntryClick: (FileEntry) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(entries, key = { it.path }) { entry ->
            FileRow(entry = entry, source = source, onClick = { onEntryClick(entry) })
        }
        item {
            Text(
                "共 ${entries.size} 个项目",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, source: FileSource?, onClick: () -> Unit) {
    val tv = LocalTvMode.current
    val iconSize = if (tv) 56.dp else 46.dp
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(20.dp)
            )
            .tvFocus(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(14.dp))
        ) {
            EntryThumbnail(entry = entry, source = source, badgeSize = iconSize)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                entrySubtitle(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.isDirectory) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FileGrid(
    entries: List<FileEntry>,
    source: FileSource?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onEntryClick: (FileEntry) -> Unit
) {
    val tv = LocalTvMode.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (tv) 180.dp else 106.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(entries, key = { it.path }) { entry ->
            GridCell(entry = entry, source = source, onClick = { onEntryClick(entry) })
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                "共 ${entries.size} 个项目",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun GridCell(entry: FileEntry, source: FileSource?, onClick: () -> Unit) {
    val accent = TomatoAccents.forKind(entry.kind)
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp)
            )
            .tvFocus(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.03f))
                    )
                )
        ) {
            EntryThumbnail(entry = entry, source = source, badgeSize = 52.dp)
            if (entry.isDirectory) {
                Text(
                    "文件夹",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                Text(
                    formatBytes(entry.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EntryThumbnail(
    entry: FileEntry,
    source: FileSource?,
    badgeSize: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val repository = context.tomatoContainer.repository
    if (!entry.isImage || source == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FileTypeBadge(entry.kind, size = badgeSize)
        }
        return
    }
    var result by remember(entry.path, source.id) { mutableStateOf<Result<File>?>(null) }
    LaunchedEffect(entry.path, source.id) {
        result = runCatching { repository.localFile(source, entry) }
    }
    val file = result?.getOrNull()
    when {
        file != null -> AsyncImage(
            model = ImageRequest.Builder(context).data(file).crossfade(true).build(),
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = TomatoAccents.forKind(PreviewKind.IMAGE)
            )
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FileTypeBadge(PreviewKind.IMAGE, size = badgeSize)
        }
    }
}

private fun entrySubtitle(entry: FileEntry): String {
    if (entry.isDirectory) return "文件夹"
    val type = when (entry.kind) {
        PreviewKind.IMAGE -> "图片"
        PreviewKind.PDF -> "PDF"
        PreviewKind.TEXT -> "文本"
        PreviewKind.DOCX -> "Word 文档"
        PreviewKind.XLSX -> "Excel 表格"
        PreviewKind.PPTX -> "演示文稿"
        PreviewKind.AUDIO -> "音频"
        PreviewKind.VIDEO -> "视频"
        PreviewKind.ARCHIVE -> "压缩包"
        else -> "文件"
    }
    val date = if (entry.lastModified > 0) " · ${formatDate(entry.lastModified)}" else ""
    return "$type · ${formatBytes(entry.size)}$date"
}

private fun sortEntries(entries: List<FileEntry>, mode: SortMode): List<FileEntry> {
    val byName = Comparator<FileEntry> { a, b -> NaturalOrder.compare(a.name, b.name) }
    val comparator: Comparator<FileEntry> = when (mode) {
        SortMode.NAME_ASC -> byName
        SortMode.NAME_DESC -> byName.reversed()
        SortMode.DATE_DESC -> compareByDescending { it.lastModified }
        SortMode.SIZE_DESC -> compareByDescending { it.size }
        SortMode.TYPE -> compareBy<FileEntry> { it.kind.ordinal }.then(byName)
    }
    return entries.sortedWith(
        compareByDescending<FileEntry> { it.isDirectory }.then(comparator)
    )
}