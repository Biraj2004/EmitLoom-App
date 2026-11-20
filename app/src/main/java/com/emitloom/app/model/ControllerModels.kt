// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.model

import com.emitloom.app.protocol.ControllerProfile

data class ColorRgb(val red: Int, val green: Int, val blue: Int) {
    fun toHex(): String = "#%02X%02X%02X".format(red, green, blue)

    companion object {
        fun fromHex(value: String): ColorRgb? {
            val hex = value.trim().removePrefix("#").takeIf { it.length == 6 } ?: return null
            val rgb = hex.toIntOrNull(16)?.takeIf { it in 0..0xffffff } ?: return null
            return ColorRgb(rgb shr 16, rgb shr 8 and 255, rgb and 255)
        }
    }
}

data class ControllerDevice(val address: String, val model: String, val profile: ControllerProfile)

enum class LightMode(val wireId: Int, val label: String) {
    COLOR(1, "Color"), WHITE(2, "White"), EFFECTS(3, "Effects"), WHITE_EFFECTS(4, "White effects"),
    MUSIC(5, "Sound"), WHITE_MUSIC(6, "White sound"), CUSTOM(7, "Custom")
}

data class LightState(
    val power: Boolean,
    val brightness: Int,
    val color: ColorRgb,
    val mode: LightMode,
    val effect: Int,
    val speed: Int,
    val sensitivity: Int,
    val length: Int = 0,
    val forward: Boolean = true,
    val playing: Boolean = true,
    val loop: Boolean = false,
    val input: Int = 0,
    val white: Int = 0,
    val cold: Int = 255,
    val warm: Int = 0,
    val lightType: Int = 0,
    val order: Int = 0,
    val firmware: String = "",
    val powerRestore: Int = 0,
    val coexist: Boolean = false
)

enum class LightType(val id: Int, val label: String, val order: String) {
    PWM_MONO(1, "PWM · Single color", ""), SPI_MONO(2, "SPI · Single color", "123"),
    PWM_CCT(3, "PWM · Tunable white", "CW"), SPI_CCT(4, "SPI · Tunable white", "CWX"),
    PWM_RGB(5, "PWM · RGB", "RGB"), SPI_RGB(6, "SPI · RGB", "RGB"),
    PWM_RGBW(7, "PWM · RGBW", "RGBW"), SPI_RGBW(8, "SPI · RGBW", "RGBW"),
    SPI_RGB_PWM_W(9, "SPI RGB + PWM white", "RGB"), PWM_RGBCCT(10, "PWM · RGB + tunable white", "RGBCW"),
    SPI_RGBCCT(11, "SPI · RGB + tunable white", "RGBCW"),
    SPI_RGB_PWM_CCT(12, "SPI RGB + PWM tunable white", "RGB"),
    SPI_CCT_ALT(13, "SPI · Tunable white (alternate)", "CWX"),
    SPI_RGBCCT_ALT(14, "SPI · RGB + tunable white (alternate)", "RGBCW");

    val rgb get() = order.startsWith("RGB")
    val white get() = this != PWM_RGB && this != SPI_RGB
    val cct get() = id in setOf(3, 4, 10, 11, 12, 13, 14)
    val spi get() = name.startsWith("SPI")
    val coexist get() = rgb && white

    companion object {
        fun fromWire(value: Int) = entries.firstOrNull { it.id == (value and 0x7f) }
    }
}

