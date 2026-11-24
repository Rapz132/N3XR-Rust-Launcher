package com.movtery.zalithlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.viewmodel.LocalBackgroundViewModel

/**
 * Panel bergaya kaca (glass): isi semi-transparan dengan gradasi tipis,
 * plus garis tepi terang di bagian atas seperti pantulan cahaya.
 * Kalau ada background gambar/video, panel dibuat lebih transparan supaya background terlihat tembus.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val hasBackground = LocalBackgroundViewModel.current?.isValid == true

    val baseAlpha = if (hasBackground) 0.42f else 0.78f
    val fill = Brush.verticalGradient(
        listOf(
            surface.copy(alpha = (baseAlpha + 0.12f).coerceAtMost(1f)),
            surface.copy(alpha = (baseAlpha - 0.10f).coerceAtLeast(0f))
        )
    )
    val edge = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.30f else 0.75f),
            Color.White.copy(alpha = if (isDark) 0.05f else 0.20f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = fill, shape = shape)
            .border(width = 1.dp, brush = edge, shape = shape)
    ) {
        content()
    }
}
