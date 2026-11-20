// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.model

import com.emitloom.app.protocol.ControllerProfile

data class LightEffect(val id: Int, val name: String)

private fun named(start: Int, vararg names: String) = names.mapIndexed { index, name -> LightEffect(start + index, name) }

private val hues = listOf("Red", "Green", "Blue", "Yellow", "Cyan", "Purple", "White")
private fun colored(start: Int, effect: String) = named(start, *hues.map { "$it $effect" }.toTypedArray())
private val huePairs = hues.flatMapIndexed { index, first -> hues.drop(index + 1).map { "$first / $it" } }

private val soundEffects = arrayOf(
    "Spectrum", "Single-color Spectrum", "Rhythm Stars", "Single-color Rhythm Stars",
    "Energy Gradient", "Single-color Energy", "Pulse Gradient", "Single-color Pulse",
    "Forward Ejection", "Single-color Forward Ejection", "Reverse Ejection", "Single-color Reverse Ejection",
    "VU Meter", "Single-color VU Meter", "Love & Peace", "Christmas", "Heartbeat", "Party"
)
private val compactSound = arrayOf("Sound Breath", "Sound Jump", "Single-color Sound Breath")
private val sceneSpiStart = named(
    1, "Rainbow", "Rainbow Meteor", "Rainbow Comet", "Rainbow Segments", "Rainbow Wave",
    "Rainbow Jump", "Rainbow Stars", "Rainbow Spin", "Red / Yellow Fire", "Red / Purple Fire",
    "Green / Yellow Fire", "Green / Cyan Fire", "Blue / Purple Fire", "Blue / Cyan Fire"
)
private val a0Start = named(1, "Rainbow", "Rainbow Meteor", "Rainbow Stars", "Rainbow Spin") +
    sceneSpiStart.subList(8, 14).map { it.copy(id = it.id - 4) }
private val scenePwm = named(1, "Color Jump", "Color Breath", "Color Strobe", "Color Heartbeat", "Color Gradient") +
    colored(6, "Breath")
private val compact = named(1, "Color Gradient", "Color Jump", "Color Breath", "Color Strobe") +
    colored(5, "Breath") + colored(12, "Strobe") + named(32, "Custom-color Breath", "Custom-color Strobe")
private val dual = named(
    1, "Rainbow", "Rainbow Stars", "Twinkle", "Fire", "Stack", "Comet", "Wave", "Chase",
    "Red / Blue / White", "Green / Yellow / White", "Red / Green / White", "Red / Yellow",
    "Red / White", "Green / White", "Gradient", "Wipe", "Breath", "Full-color Comet Wipe",
    "Comet Wipe", "Dot Wipe", "Full-color Meteor Rain", "Meteor Rain", "Color Dots", "Color Blocks"
)
private val dualSound = named(
    0x65, "Spectrum", "Single-color Spectrum", "Rhythm Stars", "Single-color Rhythm Stars",
    "Full-color Beat", "Single-color Beat", "Energy Gradient", "Single-color Energy", "Pulse Gradient",
    "Single-color Pulse", "Full-color Ripple", "Single-color Ripple", "Love & Peace", "Christmas",
    "Heartbeat", "Party"
)

// The two SPI catalogs share the same sequence after their model-specific rainbow and fire effects.
private fun sharedSpiPatterns(start: Int): List<LightEffect> {
    val names = buildList {
        addAll(hues.map { "$it Comet" })
        addAll(hues.map { "$it Meteor" })
        addAll(huePairs.map { "$it Gradient Chase" })
        addAll(hues.map { "$it Wave" })
        addAll(huePairs.map { "$it Wave" })
        addAll(hues.map { "$it Stars" })
        addAll(hues.dropLast(1).map { "$it Backlit Stars" })
        addAll(hues.map { "White Stars on $it" })
        addAll(hues.map { "$it Breath" })
        addAll(hues.map { "$it Stack" })
        add("Full-color Stack")
        addAll(hues.zipWithNext { first, second -> "$first / $second Stack" })
        addAll(listOf(
            "Red / Blue / White Chase", "Green / Yellow / White Chase", "Red / Green / White Chase",
            "Red / Yellow Chase", "Red / White Chase", "Green / White Chase"
        ))
        addAll(hues.map { "$it Comet Spin" })
        addAll(hues.map { "$it Dot Spin" })
        addAll(hues.map { "$it Segment Spin" })
        add("Gradient")
    }
    return names.mapIndexed { index, name -> LightEffect(start + index, name) }
}

private val sceneSpi = sceneSpiStart + sharedSpiPatterns(0x0f)
private val a0 = a0Start + sharedSpiPatterns(0x0b)

fun ControllerDevice.lightType(state: LightState) =
    LightType.fromWire(state.lightType).takeIf { profile == ControllerProfile.FRAMED }

fun ControllerDevice.hasRgb(state: LightState) = lightType(state)?.rgb ?: true

fun ControllerDevice.hasWhite(state: LightState) =
    lightType(state)?.white ?: (model in setOf("SP617E", "SP614E", "SP624E"))

fun ControllerDevice.hasControllerMic() = model !in setOf("SP621E", "SP623E", "SP624E")

fun ControllerDevice.effects(state: LightState, mode: LightMode): List<LightEffect> {
    val type = lightType(state)
    val whiteSpi = type?.spi == true && type.id !in setOf(9, 12)
    return when (mode) {
        LightMode.EFFECTS -> when (profile) {
            ControllerProfile.FRAMED -> if (type?.spi == true) sceneSpi else scenePwm
            ControllerProfile.A0 -> a0
            ControllerProfile.COMPACT -> compact
            ControllerProfile.DUAL -> dual
        }
        LightMode.WHITE_EFFECTS -> if (whiteSpi) {
            named(1, "White Breath", "White Stars", "White Meteor", "White Comet Spin",
                "White Dot Spin", "White Segment Spin", "White Chase", "White Comet", "White Wave", "White Stack")
        } else named(1, "White Breath", "White Strobe", "White Heartbeat")
        LightMode.MUSIC -> when (profile) {
            ControllerProfile.FRAMED -> if (type?.spi == true) named(1, *soundEffects) else named(0, *compactSound)
            ControllerProfile.A0 -> named(profile.firstSound, *soundEffects)
            ControllerProfile.COMPACT -> named(profile.firstSound, *compactSound)
            ControllerProfile.DUAL -> dualSound
        }
        LightMode.WHITE_MUSIC -> if (whiteSpi) named(1, "White Blink", "White Energy", "White Hits",
            "White Forward Ejection", "White Reverse Ejection") else named(1, "White Sound Breath")
        else -> emptyList()
    }
}

fun chipOrders(sequence: String): List<String> {
    if (sequence.isEmpty()) return emptyList()
    if (sequence.length == 1) return listOf(sequence)
    val permutations = sequence.indices.flatMap { index ->
        chipOrders(sequence.removeRange(index, index + 1)).map { sequence[index] + it }
    }
    if (sequence.length <= 3) return permutations
    return (chipOrders(sequence.take(3)).map { it + sequence.drop(3) } + permutations).distinct()
}
