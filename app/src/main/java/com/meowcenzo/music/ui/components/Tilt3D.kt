package com.meowcenzo.music.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Real 3D tilt (perspective rotation) for a layout. Two sources, added together:
 *  1. Press and hold anywhere on it: tilts toward your finger, springs back on release.
 *     It never consumes the touch, so the HorizontalPager swipe still works.
 *  2. Device motion (accelerometer): subtle parallax when you move the phone.
 *
 * Shape, border and colours are untouched; this only rotates the existing layer.
 * Put it BEFORE your existing .graphicsLayer / .border modifiers.
 */
@Composable
fun Modifier.tilt3D(
    maxDegrees: Float = 12f,
    useSensor: Boolean = true,
): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var touchX by remember { mutableFloatStateOf(0f) }   // rotationX from touch
    var touchY by remember { mutableFloatStateOf(0f) }   // rotationY from touch
    var sensorX by remember { mutableFloatStateOf(0f) }
    var sensorY by remember { mutableFloatStateOf(0f) }
    val releaseJob = remember { arrayOfNulls<Job>(1) }

    if (useSensor) {
        DisposableEffect(Unit) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm == null || accel == null) return@DisposableEffect onDispose { }

            var fastX = 0f; var fastY = 0f
            var slowX = 0f; var slowY = 0f
            var primed = false

            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val x = e.values[0]
                    val y = e.values[1]
                    if (!primed) { fastX = x; fastY = y; slowX = x; slowY = y; primed = true }
                    // fast = where the phone is now, slow = how you normally hold it.
                    fastX += (x - fastX) * 0.12f
                    fastY += (y - fastY) * 0.12f
                    slowX += (x - slowX) * 0.004f
                    slowY += (y - slowY) * 0.004f
                    val k = maxDegrees * 2.2f / 9.81f
                    sensorY = ((slowX - fastX) * k).coerceIn(-maxDegrees, maxDegrees)
                    sensorX = ((fastY - slowY) * k).coerceIn(-maxDegrees, maxDegrees)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sm.unregisterListener(listener) }
        }
    }

    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                releaseJob[0]?.cancel()

                fun aim(p: Offset) {
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    val nx = ((p.x / w - 0.5f) * 2f).coerceIn(-1f, 1f)
                    val ny = ((p.y / h - 0.5f) * 2f).coerceIn(-1f, 1f)
                    touchY = nx * maxDegrees
                    touchX = -ny * maxDegrees
                }

                aim(down.position)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break          // pager took over the swipe
                    aim(change.position)
                } while (event.changes.any { it.pressed })

                val startX = touchX
                val startY = touchY
                releaseJob[0] = scope.launch {
                    animate(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    ) { k, _ ->
                        touchX = startX * k
                        touchY = startY * k
                    }
                }
            }
        }
        .graphicsLayer {
            rotationX = (sensorX + touchX).coerceIn(-maxDegrees * 1.5f, maxDegrees * 1.5f)
            rotationY = (sensorY + touchY).coerceIn(-maxDegrees * 1.5f, maxDegrees * 1.5f)
            cameraDistance = 16f * density
        }
}