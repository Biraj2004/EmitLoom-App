// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.emitloom.app.model.ColorRgb
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ColorWheel(color: ColorRgb, onColor: (ColorRgb) -> Unit, modifier: Modifier = Modifier) {
    val selection = remember(color) {
        FloatArray(3).also { AndroidColor.RGBToHSV(color.red, color.green, color.blue, it) }
    }
    val onChange by rememberUpdatedState(onColor)
    val spectrum = remember { listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red) }
    Canvas(
        modifier.aspectRatio(1f).padding(12.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f
                fun select(position: Offset) {
                    val offset = position - center
                    val hue = (Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())).toFloat() + 360f) % 360f
                    val saturation = (offset.getDistance() / radius).coerceIn(0f, 1f)
                    val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f))
                    onChange(ColorRgb(AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb)))
                }
                if ((down.position - center).getDistance() <= radius) {
                    down.consume()
                    select(down.position)
                    drag(down.id) { change ->
                        select(change.position)
                        change.consume()
                    }
                }
            }
        }
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(spectrum, center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
        val theta = Math.toRadians(selection[0].toDouble())
        val thumb = center + Offset(cos(theta).toFloat(), sin(theta).toFloat()) * (radius * selection[1])
        drawCircle(Color.Black.copy(alpha = .65f), 10.dp.toPx(), thumb)
        drawCircle(Color(color.red, color.green, color.blue), 7.dp.toPx(), thumb)
        drawCircle(Color.White, 8.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
    }
}
