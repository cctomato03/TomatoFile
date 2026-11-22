package com.cctomato.tomatofile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.cctomato.tomatofile.data.model.PreviewKind
import com.cctomato.tomatofile.data.model.SourceType

val TomatoLightColors = lightColorScheme(
    primary = Color(0xFFD83A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF410000),
    secondary = Color(0xFF2F7D4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8F0C8),
    onSecondaryContainer = Color(0xFF00210C),
    tertiary = Color(0xFF9A5D00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDFA4),
    onTertiaryContainer = Color(0xFF301D00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF241917),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF241917),
    surfaceVariant = Color(0xFFF6E4DE),
    onSurfaceVariant = Color(0xFF59413B),
    outline = Color(0xFF8D6F68),
    outlineVariant = Color(0xFFE1C7C0),
    inverseSurface = Color(0xFF3A2E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    inversePrimary = Color(0xFFFFB4A6),
    surfaceTint = Color(0xFFD83A2E)
)

val TomatoDarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A6),
    onPrimary = Color(0xFF690006),
    primaryContainer = Color(0xFF93000B),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFF9CD5AC),
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF14512F),
    onSecondaryContainer = Color(0xFFB8F0C8),
    tertiary = Color(0xFFFFC96B),
    onTertiary = Color(0xFF4F2E00),
    tertiaryContainer = Color(0xFF704400),
    onTertiaryContainer = Color(0xFFFFDFA4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF5DDD8),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF5DDD8),
    surfaceVariant = Color(0xFF59413B),
    onSurfaceVariant = Color(0xFFE1C7C0),
    outline = Color(0xFFA98B84),
    outlineVariant = Color(0xFF59413B),
    inverseSurface = Color(0xFFF5DDD8),
    inverseOnSurface = Color(0xFF3A2E2B),
    inversePrimary = Color(0xFFD83A2E),
    surfaceTint = Color(0xFFFFB4A6)
)

object TomatoAccents {
    val Local = Color(0xFFEF6C00)
    val WebDav = Color(0xFF1E88E5)
    val Smb = Color(0xFF7E57C2)
    val Ftp = Color(0xFF00897B)

    val Folder = Color(0xFFF5A623)
    val Image = Color(0xFF19A974)
    val Pdf = Color(0xFFE0443A)
    val Word = Color(0xFF3D6FD8)
    val Excel = Color(0xFF2E9B5B)
    val Slides = Color(0xFFE0762A)
    val Text = Color(0xFF5C7D8A)
    val Audio = Color(0xFF8B6FE8)
    val Video = Color(0xFFD15B8C)
    val Archive = Color(0xFFAD7B36)
    val Other = Color(0xFF74838D)

    val HeroStart = Color(0xFFD92F35)
    val HeroMiddle = Color(0xFFF2643D)
    val HeroEnd = Color(0xFFF7A93B)

    fun forSource(type: SourceType): Color = when (type) {
        SourceType.LOCAL -> Local
        SourceType.WEBDAV -> WebDav
        SourceType.SMB -> Smb
        SourceType.FTP -> Ftp
    }

    fun forKind(kind: PreviewKind): Color = when (kind) {
        PreviewKind.FOLDER -> Folder
        PreviewKind.IMAGE -> Image
        PreviewKind.PDF -> Pdf
        PreviewKind.DOCX -> Word
        PreviewKind.XLSX -> Excel
        PreviewKind.PPTX -> Slides
        PreviewKind.TEXT -> Text
        PreviewKind.AUDIO -> Audio
        PreviewKind.VIDEO -> Video
        PreviewKind.ARCHIVE -> Archive
        PreviewKind.OTHER -> Other
    }
}

object TomatoGradients {
    val hero = listOf(TomatoAccents.HeroStart, TomatoAccents.HeroMiddle, TomatoAccents.HeroEnd)

    fun forSource(type: SourceType): List<Color> = when (type) {
        SourceType.LOCAL -> listOf(Color(0xFFF57C00), Color(0xFFFFB74D))
        SourceType.WEBDAV -> listOf(Color(0xFF1E88E5), Color(0xFF64B5F6))
        SourceType.SMB -> listOf(Color(0xFF7E57C2), Color(0xFFB39DDB))
        SourceType.FTP -> listOf(Color(0xFF00897B), Color(0xFF4DB6AC))
    }
}