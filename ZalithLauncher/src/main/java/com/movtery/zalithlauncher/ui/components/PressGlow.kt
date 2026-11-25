package com.movtery.zalithlauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Efek bercahaya (glow) dan sedikit mengecil saat elemen ditekan.
 * Hanya mengamati sentuhan, tidak mengganggu klik di dalamnya.
 *
 * @param color warna cahaya, bawaan: warna utama tema
 * @param persistent kalau true, cahaya tipis tetap menyala (misalnya untuk item yang sedang dipilih)
 */
fun Modifier.pressGlow(
    color: Color? = null,
    persistent: Boolean = false
): Modifier = composed {
    val glowColor = color ?: MaterialTheme.colorScheme.primary
    var pressed by remember { mutableStateOf(false) }
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            pressed -> 0.55f
            persistent -> 0.18f
            else -> 0f
        },
        animationSpec = tween(180),
        label = "pressGlowAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "pressGlowScale"
    )

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (glowAlpha > 0.01f) {
                val radius = size.maxDimension * 0.8f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
}
