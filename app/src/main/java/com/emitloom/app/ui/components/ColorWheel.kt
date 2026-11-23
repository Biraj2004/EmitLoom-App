// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.emitloom.app.model.ColorRgb
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ColorWheel(color: ColorRgb, onColor: (ColorRgb) -> Unit, modifier: Modifier = Modifier) {
    val selection = rememberUpdatedState(color)
    val onChange by rememberUpdatedState(onColor)
    val haptics = LocalHapticFeedback.current
    val spectrum = remember { listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red) }
    Spacer(
        modifier.aspectRatio(1f).semantics {
            contentDescription = "Color wheel"
            stateDescription = color.toHex()
        }.pointerInput(haptics) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (minOf(size.width, size.height) / 2f - 12.dp.toPx()).coerceAtLeast(1f)
                var lastColor: ColorRgb? = null
                fun select(position: Offset) {
                    val offset = position - center
                    val hue = (Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())).toFloat() + 360f) % 360f
                    val saturation = (offset.getDistance() / radius).coerceIn(0f, 1f)
                    val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f))
                    val next = ColorRgb(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb))
                    if (next != lastColor) {
                        lastColor = next
                        onChange(next)
                    }
                }
                if ((down.position - center).getDistance() <= radius + 8.dp.toPx()) {
                    down.consume()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    select(down.position)
                    drag(down.id) { change ->
                        select(change.position)
                        change.consume()
                    }
                }
            }
        }.drawWithCache {
            val radius = (size.minDimension / 2f - 12.dp.toPx()).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val hue = Brush.sweepGradient(spectrum, center)
            val saturation = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius)
            val hsv = FloatArray(3)
            onDrawBehind {
                val (red, green, blue) = selection.value
                AndroidColor.RGBToHSV(red, green, blue, hsv)
                val angle = Math.toRadians(hsv[0].toDouble())
                val thumb = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius * hsv[1])
                drawCircle(hue, radius, center)
                drawCircle(saturation, radius, center)
                drawCircle(Color.Black.copy(alpha = .7f), 11.dp.toPx(), thumb)
                drawCircle(Color(red, green, blue), 8.dp.toPx(), thumb)
                drawCircle(Color.White, 8.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
            }
        }
    )
}
