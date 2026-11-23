package com.cctomato.tomatofile.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import com.cctomato.tomatofile.R
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.data.model.SourceType
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.EmptyState
import com.cctomato.tomatofile.ui.components.RoundIconButton
import com.cctomato.tomatofile.ui.components.SectionTitle
import com.cctomato.tomatofile.ui.components.SourceBadge
import com.cctomato.tomatofile.ui.components.sourceIcon
import com.cctomato.tomatofile.ui.theme.TomatoAccents
import com.cctomato.tomatofile.ui.theme.TomatoGradients
import com.cctomato.tomatofile.ui.tv.tvFocus
import com.cctomato.tomatofile.util.StoragePermissions
import com.cctomato.tomatofile.util.formatBytes
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenSource: (FileSource) -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (FileSource) -> Unit,
    onOpenCache: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(StoragePermissions.hasAllFilesAccess(context)) }
    var deleteTarget by remember { mutableStateOf<FileSource?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = StoragePermissions.hasAllFilesAccess(context)
    }

    // The standard card home is used on both phones and TV: it is a LazyColumn,
    // scrolls reliably and every interactive card already receives tvFocus styling.
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAddSource,
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("添加存储", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SimpleHomeHeader(onOpenSettings = onOpenSettings)
                }
                if (!hasPermission) {
                    item {
                        PermissionBanner(
                            onRequest = {
                                StoragePermissions.requestAllFilesAccess(context)
                            }
                        )
                    }
                }
                item {
                    SectionTitle(
                        title = "存储位置",
                        subtitle = if (sources.isEmpty()) "还没有配置任何存储" else "共 ${sources.size} 个存储"
                    )
                }
                items(sources, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        onClick = { onOpenSource(source) },
                        onEdit = { onEditSource(source) },
                        onDelete = { deleteTarget = source },
                        onClearCache = {
                            viewModel.clearSourceCache(source)
                        }
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除存储") },
            text = { Text("确定删除「${target.name}」吗？这只移除配置，不会删除远程文件。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteSource(target) { removed ->
                        if (!removed) {
                            // Built-in sources are protected; nothing else to do here.
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SimpleHomeHeader(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TomatoFile",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
        RoundIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "设置",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun HeroHeader(
    sourceCount: Int,
    cacheBytes: Long,
    onOpenCache: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(TomatoGradients.hero))
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * 0.52f,
                center = Offset(size.width * 0.92f, size.height * 0.06f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.32f,
                center = Offset(size.width * 0.10f, size.height * 0.95f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.05f),
                radius = size.minDimension * 0.18f,
                center = Offset(size.width * 0.72f, size.height * 0.86f)
            )
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_tomato_logo),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TomatoFile",
                        color = Color.White,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "本地、WebDAV、SMB、FTP，一处浏览",
                        color = Color.White.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                RoundIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    tint = Color.White,
                    background = Color.White.copy(alpha = 0.16f)
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(
                    icon = Icons.Rounded.Storage,
                    label = "$sourceCount 个存储",
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    icon = Icons.Rounded.DataUsage,
                    label = "缓存 ${formatBytes(cacheBytes)}",
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenCache)
                )
            }
        }
    }
}

@Composable
private fun HeroStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .tvFocus(RoundedCornerShape(22.dp))
            .clickable(onClick = onRequest)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "开启完整文件访问",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "否则「本机存储」中的部分目录无法读取",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
        }
        Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun SourceCard(
    source: FileSource,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClearCache: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = TomatoAccents.forSource(source.type)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .tvFocus(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.09f), Color.Transparent)
                    )
                )
                .padding(start = 14.dp, end = 6.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBadge(source.type)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        source.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        source.type.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                    if (source.builtIn) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "内置",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    source.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "存储操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("清空此存储缓存") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClearCache()
                        }
                    )
                    if (!source.builtIn) {
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSourceCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(24.dp)
            )
            .tvFocus(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("连接新的存储", style = MaterialTheme.typography.titleMedium)
            Text(
                "WebDAV · SMB · FTP · 本地目录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CacheOverviewCard(bytes: Long, maxBytes: Long, itemCount: Int, onClick: () -> Unit) {
    val ratio = if (maxBytes == Long.MAX_VALUE || maxBytes <= 0L) 0f
    else (bytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .animateContentSize()
            .tvFocus(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "缓存管理",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "${itemCount} 个资源 · ${formatBytes(bytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                    )
                }
                Text(
                    "管理",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun TvHomeScreen(
    sources: List<FileSource>,
    stats: com.cctomato.tomatofile.data.model.CacheStats,
    hasPermission: Boolean,
    onOpenSource: (FileSource) -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (FileSource) -> Unit,
    onOpenCache: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(sources.size, hasPermission) {
        delay(250L)
        runCatching { firstFocus.requestFocus() }
    }

    AppBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 56.dp, vertical = 30.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_tomato_logo),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp)
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TomatoFile",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "本地、WebDAV、SMB、FTP · 大屏影院模式",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TvCachePill(
                    bytes = stats.totalBytes,
                    count = stats.itemCount,
                    onClick = onOpenCache
                )
                Spacer(Modifier.width(14.dp))
                RoundIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings
                )
            }

            Spacer(Modifier.height(24.dp))
            if (!hasPermission) {
                PermissionBanner(onRequest = onRequestPermission)
                Spacer(Modifier.height(18.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("存储位置", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${sources.size} 个存储",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                    TvSourceTile(
                        source = source,
                        onClick = { onOpenSource(source) },
                        onEdit = { onEditSource(source) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    )
                }
                item {
                    TvAddTile(
                        onClick = onAddSource,
                        modifier = if (sources.isEmpty()) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TvQuickTile(
                    icon = Icons.Rounded.DataUsage,
                    title = "缓存管理",
                    subtitle = "${formatBytes(stats.totalBytes)} · ${stats.itemCount} 个资源",
                    onClick = onOpenCache,
                    modifier = Modifier.weight(1f)
                )
                TvQuickTile(
                    icon = Icons.Rounded.Settings,
                    title = "设置",
                    subtitle = "主题、权限与 TV 布局",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "使用方向键移动焦点，按 OK 键打开 · 视频播放支持 4K / HEVC / AV1（取决于设备解码能力）",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TvCachePill(bytes: Long, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .tvFocus(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.DataUsage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "缓存 ${formatBytes(bytes)} · $count",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun TvSourceTile(
    source: FileSource,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .width(330.dp)
            .height(196.dp)
            .clip(shape)
            .background(Brush.linearGradient(TomatoGradients.forSource(source.type)))
            .tvFocus(shape, scaleUp = 1.05f)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        sourceIcon(source.type),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    source.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .tvFocus(CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "编辑存储",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                source.name,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                source.summary(),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvAddTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .width(330.dp)
            .height(196.dp)
            .clip(shape)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .tvFocus(shape, scaleUp = 1.05f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("添加存储", style = MaterialTheme.typography.titleMedium)
        Text(
            "WebDAV · SMB · FTP · 本机",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TvQuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(104.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .tvFocus(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}