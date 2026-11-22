package com.cctomato.tomatofile.ui.cache

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cctomato.tomatofile.data.model.CacheStats
import com.cctomato.tomatofile.data.model.FileSource
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.EmptyState
import com.cctomato.tomatofile.ui.components.ScreenTopBar
import com.cctomato.tomatofile.ui.components.SourceBadge
import com.cctomato.tomatofile.ui.theme.TomatoGradients
import com.cctomato.tomatofile.util.formatBytes

@Composable
fun CacheScreen(
    onBack: () -> Unit,
    viewModel: CacheViewModel = viewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val limitMb by viewModel.cacheLimitMb.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }
    var sourceToClear by remember { mutableStateOf<FileSource?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = "缓存管理",
                subtitle = "网络文件只下载一次",
                onBack = onBack
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { CacheHero(stats = stats, busy = busy) }
                item {
                    LimitSelector(
                        selectedMb = limitMb,
                        onSelect = { viewModel.setCacheLimit(it) }
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "浏览远程文件时，图片、PDF、文本与 Office 文件会缓存到本地。再次打开同一文件时直接读取缓存，远程文件发生变化后会自动刷新。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text(
                        "按存储查看",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                val usage = stats.perSource.filterValues { it > 0 }
                if (usage.isEmpty()) {
                    item {
                        EmptyState(
                            title = "还没有缓存",
                            message = "打开网络存储中的图片或文档后，这里会出现可清理的缓存。",
                            icon = Icons.Rounded.Storage
                        )
                    }
                } else {
                    items(usage.entries.toList(), key = { it.key }) { (sourceId, bytes) ->
                        val source = sources.firstOrNull { it.id == sourceId }
                        SourceCacheRow(
                            source = source,
                            sourceId = sourceId,
                            bytes = bytes,
                            onClear = {
                                source?.let { sourceToClear = it }
                            }
                        )
                    }
                }
                item {
                    if (stats.totalBytes > 0) {
                        OutlinedButton(
                            onClick = { confirmClearAll = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            enabled = !busy
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在清理…")
                            } else {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("清空全部缓存", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部缓存") },
            text = {
                Text("将删除 ${formatBytes(stats.totalBytes)} 的远程文件缓存和缩略图缓存。原始文件不会被删除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.clearAll()
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }

    sourceToClear?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceToClear = null },
            title = { Text("清空此存储的缓存") },
            text = { Text("将删除「${source.name}」下载过的所有缓存文件。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSource(source.id)
                    sourceToClear = null
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sourceToClear = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CacheHero(stats: CacheStats, busy: Boolean) {
    val unlimited = stats.maxBytes == Long.MAX_VALUE
    val ratio = if (unlimited || stats.maxBytes <= 0L) 0f
    else (stats.totalBytes.toFloat() / stats.maxBytes.toFloat()).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(TomatoGradients.hero))
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已使用缓存", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatBytes(stats.totalBytes),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (unlimited) "不限制缓存大小 · ${stats.itemCount} 个资源"
                else "上限 ${formatBytes(stats.maxBytes)} · ${stats.itemCount} 个资源",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))
            if (unlimited) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            } else {
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun LimitSelector(selectedMb: Int, onSelect: (Int) -> Unit) {
    Column {
        Text("缓存上限", style = MaterialTheme.typography.titleLarge)
        Text(
            "达到上限后会自动清理最久未使用的文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf(
                256 to "256 MB",
                512 to "512 MB",
                1024 to "1 GB",
                2048 to "2 GB",
                0 to "不限"
            )) { (value, label) ->
                val selected = value == selectedMb
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                Brush.linearGradient(TomatoGradients.hero)
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceCacheRow(
    source: FileSource?,
    sourceId: String,
    bytes: Long,
    onClear: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (source != null) {
            SourceBadge(source.type, size = 42.dp)
        } else {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Storage, contentDescription = null)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                source?.name ?: "已删除的存储",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${formatBytes(bytes)} 缓存",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClear) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}