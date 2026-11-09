package com.meowcenzo.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * LED-style segmented level meter in the same language as TerminalSeekBar:
 * sharp rectangles, Meow.TextHi (lit), Meow.TextLo (peak-hold), Meow.PanelHigh (off).
 *
 * NOTE: this is a decorative animation driven by time, not real FFT data.
 * Real FFT needs android.media.audiofx.Visualizer + RECORD_AUDIO permission.
 * It idles (no frame callbacks) when paused, so it costs no battery.
 */
@Composable
fun TerminalVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    columns: Int = 28,
    rows: Int = 8,
) {
    val playing by rememberUpdatedState(isPlaying)
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(450),
        label = "vizEnergy",
    )
    val levels = remember(columns) { FloatArray(columns) }
    val peaks = remember(columns) { FloatArray(columns) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(columns) {
        var last = 0L
        var t = 0f
        while (true) {
            val idle = !playing && energy < 0.01f &&
                    levels.all { it < 0.01f } && peaks.all { it < 0.01f }
            if (idle) {
                last = 0L
                delay(150)
                continue
            }
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f
                else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                t += dt
                if (t > 1000f) t -= 1000f

                for (i in 0 until columns) {
                    val x = i.toFloat() / columns
                    val slow = 0.5f + 0.5f * sin(t * (1.4f + x * 1.8f) + i * 1.7f)
                    val fast = 0.5f + 0.5f * sin(t * (4.8f + x * 3.0f) + i * 0.9f)
                    val rolloff = 1f - 0.4f * x
                    val target = (slow * 0.6f + fast * 0.4f) * rolloff * energy
                    levels[i] += (target - levels[i]) * (dt * 16f).coerceAtMost(1f)
                    peaks[i] = max(levels[i], peaks[i] - dt * 0.5f)
                }
                frame++
            }
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION")
        frame // read so the canvas redraws every animation frame

        val gap = 2.dp.toPx()
        val colW = (size.width - gap * (columns - 1)) / columns
        val segH = (size.height - gap * (rows - 1)) / rows

        for (c in 0 until columns) {
            val lit = (levels[c] * rows).roundToInt().coerceIn(0, rows)
            val peakRow = (peaks[c] * rows).roundToInt().coerceIn(0, rows) - 1
            val x = c * (colW + gap)
            for (r in 0 until rows) {
                val y = size.height - (r + 1) * segH - r * gap
                val color = when {
                    r < lit -> Meow.TextHi
                    r == peakRow -> Meow.TextLo
                    else -> Meow.PanelHigh
                }
                drawRect(color, Offset(x, y), Size(colW, segH))
            }
        }
    }
}