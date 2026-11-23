package com.cctomato.tomatofile.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cctomato.tomatofile.tomatoContainer
import kotlinx.coroutines.launch

/** True when the UI should use the large-screen / D-pad focused TV layout. */
val LocalTvMode = staticCompositionLocalOf { false }

@Composable
fun ProvideTvMode(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val forced by context.tomatoContainer.settings.forceTvLayout.collectAsStateWithLifecycle()
    val detected = remember(context, configuration.uiMode) { context.isTelevision() }
    CompositionLocalProvider(LocalTvMode provides (forced || detected), content = content)
}

fun Context.isTelevision(): Boolean {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
        uiMode == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Focus highlight for TV remotes: a primary outline plus a small scale-up. On phones/tablets this
 * is a no-op so touch feedback stays unchanged.
 */
@Composable
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(20.dp),
    scaleUp: Float = 1.04f,
    borderWidth: Dp = 2.dp
): Modifier {
    val tv = LocalTvMode.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (tv && focused) scaleUp else 1f,
        label = "tv-focus-scale"
    )
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (tv && focused) {
                Modifier.border(borderWidth, MaterialTheme.colorScheme.primary, shape)
            } else {
                Modifier
            }
        )
}

/** Lets a non-focusable text/PDF preview scroll with the TV remote's up/down keys. */
@Composable
fun Modifier.tvScrollKeys(state: LazyListState, step: Float = 320f): Modifier {
    if (!LocalTvMode.current) return this
    val scope = rememberCoroutineScope()
    return this
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> {
                    scope.launch { state.scrollBy(-step) }
                    true
                }
                Key.DirectionDown -> {
                    scope.launch { state.scrollBy(step) }
                    true
                }
                else -> false
            }
        }
}
