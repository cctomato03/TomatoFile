package com.cctomato.tomatofile.ui.source

import android.os.Environment
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cctomato.tomatofile.data.model.SourceType
import com.cctomato.tomatofile.ui.components.AppBackground
import com.cctomato.tomatofile.ui.components.GradientButton
import com.cctomato.tomatofile.ui.components.ScreenTopBar
import com.cctomato.tomatofile.ui.components.SourceBadge
import com.cctomato.tomatofile.ui.components.sourceIcon
import com.cctomato.tomatofile.ui.theme.TomatoAccents
import com.cctomato.tomatofile.ui.theme.TomatoGradients
import java.io.File

@Composable
fun AddSourceScreen(
    editId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddSourceViewModel = viewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val test by viewModel.testState.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current

    val localRoot = remember {
        Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }
    val privateDir = remember { appContext.filesDir.absolutePath }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ScreenTopBar(
                    title = if (viewModel.isEditing) "编辑存储" else "添加存储",
                    subtitle = if (viewModel.isEditing) form.name else "把散落在各处的文件收进一个应用",
                    onBack = onBack
                )
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        TypeSelector(
                            selected = form.type,
                            enabled = !viewModel.isEditing,
                            onSelected = { type ->
                                viewModel.update {
                                    it.copy(
                                        type = type,
                                        name = it.name.ifBlank { type.label + " 存储" }
                                    )
                                }
                            }
                        )
                    }
                    item {
                        FormField(
                            label = "名称",
                            value = form.name,
                            placeholder = "例如：家里的 NAS",
                            leadingIcon = Icons.Rounded.Storage,
                            onValueChange = { viewModel.update { f -> f.copy(name = it) } }
                        )
                    }

                    when (form.type) {
                        SourceType.LOCAL -> {
                            item {
                                FormField(
                                    label = "本地目录",
                                    value = form.localPath,
                                    placeholder = "/storage/emulated/0",
                                    leadingIcon = Icons.Rounded.Folder,
                                    onValueChange = { viewModel.update { f -> f.copy(localPath = it) } }
                                )
                            }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val shortcuts = listOf(
                                        "内部存储" to localRoot,
                                        "下载" to "$localRoot/Download",
                                        "图片" to "$localRoot/Pictures",
                                        "相机" to "$localRoot/DCIM/Camera",
                                        "应用私有" to privateDir
                                    )
                                    items(shortcuts) { (label, path) ->
                                        ShortcutChip(label) {
                                            viewModel.update { f -> f.copy(localPath = path) }
                                        }
                                    }
                                }
                            }
                        }
                        SourceType.WEBDAV -> {
                            item {
                                FormField(
                                    label = "WebDAV 地址",
                                    value = form.url,
                                    placeholder = "https://example.com/remote.php/dav/files/me/",
                                    leadingIcon = Icons.Rounded.Link,
                                    keyboardType = KeyboardType.Uri,
                                    onValueChange = { viewModel.update { f -> f.copy(url = it) } }
                                )
                            }
                            item {
                                FormField(
                                    label = "用户名",
                                    value = form.username,
                                    placeholder = "可留空",
                                    leadingIcon = Icons.Rounded.Person,
                                    onValueChange = { viewModel.update { f -> f.copy(username = it) } }
                                )
                            }
                            item {
                                PasswordField(
                                    value = form.password,
                                    visible = passwordVisible,
                                    onVisibilityChange = { passwordVisible = it },
                                    onValueChange = { viewModel.update { f -> f.copy(password = it) } }
                                )
                            }
                            item {
                                HintCard("兼容 Nextcloud、Alist、群晖、坚果云等 WebDAV 服务，使用 Basic 认证。")
                            }
                        }
                        SourceType.SMB -> {
                            item {
                                FormField(
                                    label = "服务器地址",
                                    value = form.host,
                                    placeholder = "192.168.1.10 或 nas.local",
                                    leadingIcon = Icons.Rounded.Dns,
                                    keyboardType = KeyboardType.Uri,
                                    onValueChange = { viewModel.update { f -> f.copy(host = it) } }
                                )
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FormField(
                                        label = "端口",
                                        value = form.port,
                                        placeholder = "445",
                                        leadingIcon = Icons.Rounded.Language,
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.weight(0.8f),
                                        onValueChange = { viewModel.update { f -> f.copy(port = it) } }
                                    )
                                    FormField(
                                        label = "共享名",
                                        value = form.share,
                                        placeholder = "shared",
                                        leadingIcon = Icons.Rounded.Folder,
                                        modifier = Modifier.weight(1.2f),
                                        onValueChange = { viewModel.update { f -> f.copy(share = it) } }
                                    )
                                }
                            }
                            item {
                                FormField(
                                    label = "子目录",
                                    value = form.rootPath,
                                    placeholder = "/",
                                    leadingIcon = Icons.Rounded.Folder,
                                    onValueChange = { viewModel.update { f -> f.copy(rootPath = it) } }
                                )
                            }
                            item {
                                FormField(
                                    label = "域 / 工作组",
                                    value = form.domain,
                                    placeholder = "可留空",
                                    leadingIcon = Icons.Rounded.Dns,
                                    onValueChange = { viewModel.update { f -> f.copy(domain = it) } }
                                )
                            }
                            item {
                                FormField(
                                    label = "用户名",
                                    value = form.username,
                                    placeholder = "可留空（匿名）",
                                    leadingIcon = Icons.Rounded.Person,
                                    onValueChange = { viewModel.update { f -> f.copy(username = it) } }
                                )
                            }
                            item {
                                PasswordField(
                                    value = form.password,
                                    visible = passwordVisible,
                                    onVisibilityChange = { passwordVisible = it },
                                    onValueChange = { viewModel.update { f -> f.copy(password = it) } }
                                )
                            }
                            item { HintCard("支持 SMB 2.0 / 2.1 / 3.x，端口通常为 445。") }
                        }
                        SourceType.FTP -> {
                            item {
                                FormField(
                                    label = "服务器地址",
                                    value = form.host,
                                    placeholder = "ftp.example.com",
                                    leadingIcon = Icons.Rounded.Dns,
                                    keyboardType = KeyboardType.Uri,
                                    onValueChange = { viewModel.update { f -> f.copy(host = it) } }
                                )
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FormField(
                                        label = "端口",
                                        value = form.port,
                                        placeholder = "21",
                                        leadingIcon = Icons.Rounded.Language,
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.weight(0.8f),
                                        onValueChange = { viewModel.update { f -> f.copy(port = it) } }
                                    )
                                    FormField(
                                        label = "根目录",
                                        value = form.rootPath,
                                        placeholder = "/",
                                        leadingIcon = Icons.Rounded.Folder,
                                        modifier = Modifier.weight(1.2f),
                                        onValueChange = { viewModel.update { f -> f.copy(rootPath = it) } }
                                    )
                                }
                            }
                            item {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("使用 FTPS（显式 TLS）", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "适用于要求加密的 FTP 服务器",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = form.secure,
                                        onCheckedChange = { enabled ->
                                            viewModel.update { f -> f.copy(secure = enabled) }
                                        }
                                    )
                                }
                            }
                            item {
                                FormField(
                                    label = "用户名",
                                    value = form.username,
                                    placeholder = "可留空（匿名）",
                                    leadingIcon = Icons.Rounded.Person,
                                    onValueChange = { viewModel.update { f -> f.copy(username = it) } }
                                )
                            }
                            item {
                                PasswordField(
                                    value = form.password,
                                    visible = passwordVisible,
                                    onVisibilityChange = { passwordVisible = it },
                                    onValueChange = { viewModel.update { f -> f.copy(password = it) } }
                                )
                            }
                            item { HintCard("使用被动模式（PASV）传输。") }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.testConnection() },
                                shape = RoundedCornerShape(16.dp),
                                enabled = !test.testing && !saving
                            ) {
                                if (test.testing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在连接…")
                                } else {
                                    Text("测试连接")
                                }
                            }
                        }
                    }
                    test.message?.let { message ->
                        item {
                            TestResultCard(message = message, success = test.success)
                        }
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "密码经 Android Keystore 加密后仅保存在本机",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    GradientButton(
                        text = if (saving) "正在保存…" else "保存存储",
                        onClick = {
                            viewModel.save { onSaved() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.CheckCircle,
                        enabled = !saving && !test.testing
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeSelector(selected: SourceType, enabled: Boolean, onSelected: (SourceType) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(SourceType.entries) { type ->
            val active = type == selected
            val accent = TomatoAccents.forSource(type)
            Column(
                Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        if (active) {
                            Brush.linearGradient(TomatoGradients.forSource(type))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            )
                        }
                    )
                    .clickable(enabled = enabled) { onSelected(type) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    sourceIcon(type),
                    contentDescription = type.label,
                    tint = if (active) Color.White else accent,
                    modifier = Modifier.size(25.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun PasswordField(
    value: String,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("密码") },
        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            IconButton(onClick = { onVisibilityChange(!visible) }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码"
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

@Composable
private fun ShortcutChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun HintCard(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun TestResultCard(message: String, success: Boolean) {
    val background = if (success) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val foreground = if (success) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = foreground)
    }
}