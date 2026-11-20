// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.model

data class ColorStep(val color: ColorRgb, val seconds: Int) {
    init { require(seconds in 1..120) }

    companion object {
        fun parse(hex: String, hold: String): ColorStep? {
            val color = ColorRgb.fromHex(hex) ?: return null
            val seconds = hold.toIntOrNull()?.takeIf { it in 1..120 } ?: return null
            return ColorStep(color, seconds)
        }
    }
}

data class ColorSequence(val id: String, val name: String, val steps: List<ColorStep>, val loop: Boolean) {
    init {
        require(name.isNotBlank() && name.length <= 40)
        require(steps.size in 2..12)
    }
}
