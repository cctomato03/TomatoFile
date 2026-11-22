package com.cctomato.tomatofile.ui.viewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pinch-to-zoom + double-tap zoom + pan. Single-finger horizontal drags are deliberately left
 * unconsumed at scale 1 so the surrounding HorizontalPager can swipe between images; while zoomed
 * the pager is disabled and all drags pan the image.
 */
@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var animationJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val center = Offset(widthPx / 2f, heightPx / 2f)

        fun clampOffset(value: Offset, atScale: Float): Offset {
            val maxX = (widthPx * (atScale - 1f) / 2f).coerceAtLeast(0f)
            val maxY = (heightPx * (atScale - 1f) / 2f).coerceAtLeast(0f)
            return Offset(
                value.x.coerceIn(-maxX, maxX),
                value.y.coerceIn(-maxY, maxY)
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx, heightPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        animationJob?.cancel()
                        var active = scale > 1.01f
                        var pastSlop = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            val pressedCount = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (!pastSlop) {
                                val panDistance = panChange.getDistance()
                                val zoomDelta = abs(1f - zoomChange)
                                if (panDistance > viewConfiguration.touchSlop ||
                                    zoomDelta > 0.01f ||
                                    pressedCount > 1
                                ) {
                                    pastSlop = true
                                    if (zoomDelta > 0.01f || pressedCount > 1) active = true
                                }
                            }
                            if (active) {
                                event.changes.forEach { if (it.pressed) it.consume() }
                                val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                                val ratio = if (scale <= 0f) 1f else newScale / scale
                                val focus = centroid - center - offset
                                val nextOffset = offset + panChange + focus * (1f - ratio)
                                scale = newScale
                                offset = if (newScale <= 1.001f) {
                                    Offset.Zero
                                } else {
                                    clampOffset(nextOffset, newScale)
                                }
                                onZoomChanged(newScale > 1.01f)
                            }
                        }
                        onZoomChanged(scale > 1.01f)
                    }
                }
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures(
                        onDoubleTap = { position ->
                            animationJob?.cancel()
                            val targetScale = if (scale > 1.01f) 1f else 2.6f
                            val targetOffset = if (targetScale == 1f) {
                                Offset.Zero
                            } else {
                                clampOffset(
                                    position - center -
                                        (position - center - offset) / scale * targetScale,
                                    targetScale
                                )
                            }
                            val startScale = scale
                            val startOffset = offset
                            animationJob = scope.launch {
                                animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = tween(220)
                                ) { fraction, _ ->
                                    scale = startScale + (targetScale - startScale) * fraction
                                    offset = Offset(
                                        startOffset.x + (targetOffset.x - startOffset.x) * fraction,
                                        startOffset.y + (targetOffset.y - startOffset.y) * fraction
                                    )
                                    onZoomChanged(scale > 1.01f)
                                }
                            }
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            content()
        }
    }
}