package com.cctomato.tomatofile.ui.settings

import android.os.Build
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cctomato.tomatofile.BuildConfig
import com.cctomato.tomatofile.R
import com.cctomato.tomatofile.data.model.ThemeMode
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.ScreenTopBar
import com.cctomato.tomatofile.ui.theme.TomatoGradients
import com.cctomato.tomatofile.util.ExternalIntents
import com.cctomato.tomatofile.util.StoragePermissions

private const val GITHUB_URL = "https://github.com/cctomato03/TomatoFile"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCache: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val cacheLimitMb by viewModel.cacheLimitMb.collectAsStateWithLifecycle()
    val forceTv by viewModel.forceTvLayout.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(StoragePermissions.hasAllFilesAccess(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = StoragePermissions.hasAllFilesAccess(context)
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(title = "设置", subtitle = "让 TomatoFile 更合你的口味", onBack = onBack)
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { SectionLabel("外观") }
                item {
                    SettingCard {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingIcon(Icons.Rounded.SettingsBrightness, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("主题模式", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "浅色、深色或跟随系统",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ThemeModeSelector(selected = themeMode, onSelect = viewModel::setThemeMode)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingIcon(Icons.Rounded.ColorLens, MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("动态取色", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        "使用壁纸配色覆盖番茄红（Android 12+）"
                                    } else {
                                        "需要 Android 12 及以上版本"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dynamic,
                                onCheckedChange = viewModel::setDynamicColor,
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingIcon(Icons.Rounded.Tv, MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("TV 大屏布局", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "大焦点与遥控器导航；Android TV 会自动启用，也可在手机上预览",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = forceTv,
                                onCheckedChange = viewModel::setForceTvLayout
                            )
                        }
                    }
                }

                item { SectionLabel("存储与权限") }
                item {
                    SettingCard {
                        SettingRow(
                            icon = Icons.Rounded.Shield,
                            title = "所有文件访问权限",
                            subtitle = if (hasPermission) "已授权，可以浏览完整的内置存储" else "未授权，部分目录可能无法读取",
                            tint = if (hasPermission) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            trailing = {
                                TextButton(onClick = {
                                    StoragePermissions.requestAllFilesAccess(context)
                                }) {
                                    Text(if (hasPermission) "管理" else "去授权")
                                }
                            }
                        )
                        SettingDivider()
                        SettingRow(
                            icon = Icons.Rounded.DataUsage,
                            title = "缓存管理",
                            subtitle = if (cacheLimitMb == 0) "上限：不限制" else "上限：${cacheLimitMb} MB",
                            onClick = onOpenCache,
                            trailing = {
                                Icon(
                                    Icons.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                item { SectionLabel("关于") }
                item { AboutCard() }

                item {
                    Text(
                        "Made with Jetpack Compose · Material 3",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) { content() }
}

@Composable
private fun SettingIcon(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val active = mode == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) {
                            Brush.linearGradient(TomatoGradients.hero)
                        } else {
                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (mode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    },
                    contentDescription = null,
                    tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tomato_logo),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("TomatoFile", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { ExternalIntents.openUrl(context, GITHUB_URL) }
            ) {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text("GitHub")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "一个色彩丰富、离线优先的 Android 文件管理器。本地存储与 WebDAV、SMB、FTP 同屏浏览，文本、PDF、Office 与图片预览一应俱全。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        FeatureLine("本地 / WebDAV / SMB / FTP")
        FeatureLine("文本 · PDF · Word / Excel / PPT 文本预览")
        FeatureLine("全屏图片浏览器 + EXIF 作者链接")
        FeatureLine("LRU 网络缓存与一键清理")
        Spacer(Modifier.height(12.dp))
        Text(
            "开源许可：MIT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(
        Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}