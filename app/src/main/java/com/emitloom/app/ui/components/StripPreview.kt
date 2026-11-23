// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.emitloom.app.model.LightEffect
import com.emitloom.app.model.LightMode
import com.emitloom.app.model.LightState
import com.emitloom.app.ui.Divider
import com.emitloom.app.ui.Ink
import com.emitloom.app.ui.Panel
import com.emitloom.app.ui.TextMuted
import com.emitloom.app.ui.asColor
import com.emitloom.app.ui.viewmodel.AudioSync
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SampleSize = 24
private val Unlit = Color(0xFF263630)

@Composable
internal fun StripPreview(
    light: LightState,
    effects: List<LightEffect>,
    audioSync: AudioSync?,
    modifier: Modifier = Modifier
) {
    val index = remember(effects, light.effect) { effects.indexOfFirst { it.id == light.effect } }
    val patterns = remember(effects) { effects.map { StripPattern(it.name) } }
    val selected = effects.getOrNull(index)
    val phase = remember(light.mode, light.effect) { mutableFloatStateOf(0f) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animating = light.power && light.playing && selected != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(phase, animating) {
        if (!animating) return@LaunchedEffect
        val durationScale = coroutineContext[MotionDurationScale]
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val scale = durationScale?.scaleFactor ?: 1f
                if (now - last >= 32_000_000L) {
                    if (scale > 0f) phase.floatValue += (now - last).coerceAtMost(100_000_000L) / 1_000_000_000f / scale
                    last = now
                }
            }
        }
    }

    val whiteMode = light.mode in setOf(LightMode.WHITE, LightMode.WHITE_EFFECTS, LightMode.WHITE_MUSIC)
    val brightness = if (whiteMode) light.white else light.brightness
    val base = if (whiteMode) {
        val warmth = light.warm.toFloat() / (light.cold + light.warm).coerceAtLeast(1)
        lerp(Color(0xFFD9EDFF), Color(0xFFFFC884), warmth)
    } else light.color.asColor()
    val status = when {
        !light.power -> "Off"
        audioSync != null -> audioSync.source.title
        selected != null && !light.playing -> "Paused"
        selected != null && light.loop -> "Cycling effects"
        selected != null -> selected.name
        else -> "${(brightness * 100f / 255).roundToInt()}% brightness"
    }
    Column(
        modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Strip preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                status, modifier = Modifier.weight(1f), color = TextMuted,
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("$SampleSize LEDs", color = TextMuted, style = MaterialTheme.typography.labelMedium)
        }
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val pad = 7.dp.toPx()
            val step = (size.width - 2 * pad).coerceAtLeast(0f) / SampleSize
            val radius = (step * .32f).coerceAtMost(5.dp.toPx())
            val curve = CornerRadius(size.height / 2f)
            drawRoundRect(Ink, cornerRadius = curve)
            drawRoundRect(Divider, cornerRadius = curve, style = Stroke(1.dp.toPx()))

            val seconds = phase.floatValue
            val effectIndex = if (index >= 0 && light.loop) (index + (seconds / 6f).toInt()) % patterns.size else index
            val pattern = patterns.getOrNull(effectIndex)
            val time = seconds * (0.26f + light.speed.coerceIn(1, 10) * .16f)
            val sound = light.mode == LightMode.MUSIC || light.mode == LightMode.WHITE_MUSIC
            val gain = (light.sensitivity / 16f).coerceIn(.06f, 1f)
            val audio = audioSync?.level?.let { .15f + .85f * it.coerceIn(0f, 1f) } ?: 1f
            val level = if (light.power) (brightness / 255f).coerceIn(0f, 1f) * audio else 0f
            val length = if (light.length > 0) light.length.coerceIn(1, 150) / 150f else .24f
            repeat(SampleSize) { pixel ->
                val position = (pixel + .5f) / SampleSize
                val travel = if (light.forward) position else 1f - position
                val intensity = if (pattern == null || level == 0f) level else {
                    level * pattern.intensity(travel, time, length, sound, gain)
                }
                val tint = if (pattern == null || whiteMode || intensity == 0f) base else pattern.tint(travel, time, base)
                val led = Offset(pad + step * (pixel + .5f), center.y)
                if (intensity > 0f) drawCircle(tint.copy(alpha = intensity * .24f), radius * 1.6f, led)
                drawCircle(lerp(Unlit, tint, intensity), radius, led)
            }
            drawRect(Divider, Offset(3.dp.toPx(), center.y - 1.dp.toPx()), Size(2.dp.toPx(), 2.dp.toPx()))
            drawRect(Divider, Offset(size.width - 5.dp.toPx(), center.y - 1.dp.toPx()), Size(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}

private enum class Pattern { GRADIENT, TRAIL, STARS, WAVE, CHASE, PULSE, STEP, FIRE, METER }

private class StripPattern(name: String) {
    private val label = name.lowercase()
    private val fixed = "single-color" in label || "custom-color" in label
    private val colors = label.split(' ', '/').mapNotNull(effectColors::get)
    private val pattern = when {
        listOf("meteor", "comet", "ejection").any { it in label } -> Pattern.TRAIL
        listOf("stars", "twinkle").any { it in label } -> Pattern.STARS
        "fire" in label -> Pattern.FIRE
        listOf("wave", "ripple").any { it in label } -> Pattern.WAVE
        listOf("chase", "spin", "stack", "wipe", "dots", "blocks", "segment").any { it in label } -> Pattern.CHASE
        listOf("breath", "pulse", "heartbeat", "beat").any { it in label } -> Pattern.PULSE
        listOf("jump", "strobe", "blink").any { it in label } -> Pattern.STEP
        listOf("spectrum", "energy", "meter").any { it in label } -> Pattern.METER
        else -> Pattern.GRADIENT
    }

    fun tint(position: Float, time: Float, base: Color): Color {
        if (fixed) return base
        val progress = when (pattern) {
            Pattern.PULSE -> time * .18f
            Pattern.STEP -> floor(time * .6f) / 7f
            Pattern.FIRE -> wave(position * 2.3f + time * 1.4f)
            else -> wrap(position - time * .35f)
        }
        if (colors.size == 1) return colors.first()
        if (colors.size >= 2) {
            val point = wrap(progress) * colors.size
            val start = point.toInt().coerceAtMost(colors.lastIndex)
            return lerp(colors[start], colors[(start + 1) % colors.size], point - start)
        }
        if (pattern == Pattern.FIRE) return lerp(Color(0xFFE63819), Color(0xFFFFD766), progress)
        return Color.hsv(wrap(progress) * 359.99f, .85f, 1f)
    }

    fun intensity(position: Float, time: Float, length: Float, sound: Boolean, gain: Float): Float {
        val span = .08f + .82f * length
        val distance = wrap(time - position)
        val envelope = when (pattern) {
            Pattern.GRADIENT -> 1f
            Pattern.TRAIL -> .06f + .94f * (1f - distance / span).coerceAtLeast(0f)
            Pattern.STARS -> .1f + .9f * wave(position * 19.2f + time * (1f + position)).let { it * it * it }
            Pattern.WAVE -> .16f + .84f * wave((position - time) / (.3f + span))
            Pattern.CHASE -> if (wrap(position * 3f - time) < span) 1f else .09f
            Pattern.PULSE -> .25f + .75f * wave(time * .6f)
            Pattern.STEP -> .25f + .75f * wave(time * .8f)
            Pattern.FIRE -> .35f + .65f * wave(position * 13f - time)
            Pattern.METER -> if (position < (.2f + .8f * wave(time)) * gain) 1f else .07f
        }
        return if (sound && pattern != Pattern.METER) envelope * (.2f + .8f * wave(time) * gain) else envelope
    }
}

private val effectColors = mapOf(
    "red" to Color(0xFFFF3F36), "green" to Color(0xFF42FF8A), "blue" to Color(0xFF4786FF),
    "yellow" to Color(0xFFFFD94C), "cyan" to Color(0xFF3CEEFF), "purple" to Color(0xFFAC6EFF),
    "white" to Color.White
)

private fun wrap(value: Float): Float = value - floor(value)
private fun wave(value: Float): Float = (sin(value * (2 * PI).toFloat()) + 1f) / 2f
