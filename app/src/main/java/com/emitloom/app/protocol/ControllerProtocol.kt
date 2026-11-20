// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.protocol

import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.LightMode
import java.util.UUID

private fun bleUuid(id: String) = UUID.fromString("0000$id-0000-1000-8000-00805f9b34fb")
internal val UART_SERVICE = bleUuid("ffe0")
internal val ALTERNATE_UART_SERVICE = bleUuid("e0ff")
internal val UART_CHARACTERISTIC = bleUuid("ffe1")
internal val CCCD = bleUuid("2902")
internal const val CONTROLLER_COMPANY_ID = 20563

enum class ControllerProfile(
    val outputs: Int,
    val solidEffect: Int,
    val firstSound: Int
) {
    FRAMED(1, 1, 1),
    A0(1, 0xbe, 0xc9),
    COMPACT(1, 0x63, 0x65),
    DUAL(2, 0x19, 0x65);

    companion object {
        private val framedModels = Regex("SP(?:63|64)[0-9A]E", RegexOption.IGNORE_CASE)
        private val models = mapOf(
            "SP601E" to DUAL, "SP611E" to A0, "SP617E" to A0, "SP620E" to A0, "SP621E" to A0,
            "SP613E" to COMPACT, "SP614E" to COMPACT, "SP623E" to COMPACT, "SP624E" to COMPACT
        )
        private val modelInName = Regex("SP[0-9]{2}[0-9A]E", RegexOption.IGNORE_CASE)

        fun advertisedModel(name: String?, manufacturer: ByteArray?): String? {
            val data = manufacturer ?: byteArrayOf()
            if (data.size >= 2 && data.u(1) == 0x10) {
                val id = data.u(0)
                val number = when (id) {
                    in 0x1f..0x29 -> "63" + (id - 0x1f).toString(16).uppercase()
                    in 0x2c..0x35 -> "64" + (id - 0x2b).toString(16).uppercase()
                    else -> null
                }
                if (number != null) return "SP${number}E"
            }
            val named = name?.let { modelInName.find(it)?.value?.uppercase() }
            if (named != null && (named in models || framedModels.matches(named))) return named
            return when (data.firstOrNull()?.toInt()?.and(255)) {
                0x01 -> "SP601E"
                0x04, in 0x10..0x15 -> "SP611E"
                0x09 -> "SP613E".takeIf { data.getOrNull(1) == 0.toByte() }
                0x0a -> "SP614E"
                0x0d, 0x16 -> "SP621E"
                0x0e -> "SP623E".takeIf { data.getOrNull(1) == 0.toByte() }
                0x0f -> "SP624E".takeIf { data.getOrNull(1) == 0.toByte() }
                0x17 -> "SP617E"
                0x1b -> "SP620E".takeIf { data.getOrNull(1) == 0x10.toByte() }
                else -> null
            }
        }

        fun identify(name: String?, manufacturer: ByteArray?): ControllerProfile? {
            val model = advertisedModel(name, manufacturer) ?: return null
            return if (framedModels.matches(model)) FRAMED else models[model]
        }
    }
}

class ControllerProtocol(val profile: ControllerProfile) {
    fun status(): ByteArray = when (profile) {
        ControllerProfile.FRAMED -> frame(0x02, 1)
        ControllerProfile.A0 -> frame(0x70)
        ControllerProfile.COMPACT -> frame(0x1d)
        ControllerProfile.DUAL -> frame(0x2f)
    }

    fun power(channel: Int, on: Boolean): ByteArray = when (profile) {
        ControllerProfile.FRAMED -> frame(0x50, on.flag)
        ControllerProfile.A0 -> frame(0x62, on.flag)
        ControllerProfile.COMPACT -> frame(0x0f, on.flag)
        ControllerProfile.DUAL -> frame(0x22, channel, on.flag)
    }

    fun color(channel: Int, color: ColorRgb, level: Int, switchMode: Boolean = true): List<ByteArray> {
        val (red, green, blue) = color
        val rgb = when (profile) {
            ControllerProfile.FRAMED -> frame(0x52, red, green, blue, level)
            ControllerProfile.A0 -> frame(0x69, red, green, blue, level)
            ControllerProfile.COMPACT -> frame(0x13, red, green, blue, level)
            ControllerProfile.DUAL -> frame(0x29, channel, red, green, blue, 0xff)
        }
        return if (switchMode) listOf(effect(channel, LightMode.COLOR, profile.solidEffect), rgb) else listOf(rgb)
    }

    fun brightness(channel: Int, level: Int, white: Boolean = false): ByteArray = when (profile) {
        ControllerProfile.FRAMED -> frame(0x51, white.flag, level)
        ControllerProfile.A0 -> if (white) frame(0x76, level, 0) else frame(0x66, level)
        ControllerProfile.COMPACT -> if (white) frame(0x21, level, 255) else frame(0x12, level)
        ControllerProfile.DUAL -> frame(0x25, channel, level)
    }

    fun white(channel: Int, level: Int, switchMode: Boolean): List<ByteArray> {
        val effectId = when (profile) {
            ControllerProfile.A0 -> 0xbf
            ControllerProfile.COMPACT -> 0xcc
            else -> 1
        }
        val command = brightness(channel, level, white = true)
        return if (switchMode) listOf(effect(channel, LightMode.WHITE, effectId), command) else listOf(command)
    }

    fun effect(channel: Int, mode: LightMode, id: Int): ByteArray = when (profile) {
        ControllerProfile.FRAMED -> frame(0x53, mode.wireId, id)
        ControllerProfile.A0 -> frame(0x63, id)
        ControllerProfile.COMPACT -> frame(0x15, id)
        ControllerProfile.DUAL -> frame(0x23, channel, id)
    }

    fun speed(channel: Int, value: Int): ByteArray = when (profile) {
        ControllerProfile.FRAMED -> frame(0x54, value)
        ControllerProfile.A0 -> frame(0x67, value)
        ControllerProfile.COMPACT -> frame(0x14, value)
        ControllerProfile.DUAL -> frame(0x26, channel, value)
    }

    fun sensitivity(channel: Int, value: Int): ByteArray = when (profile) {
        ControllerProfile.FRAMED -> frame(0x5a, value)
        ControllerProfile.A0 -> frame(0x6b, value)
        ControllerProfile.COMPACT -> frame(0x17, value)
        ControllerProfile.DUAL -> frame(0x2b, channel, value)
    }

    fun length(channel: Int, value: Int): ByteArray? = when (profile) {
        ControllerProfile.FRAMED -> frame(0x55, value)
        ControllerProfile.A0 -> frame(0x68, value)
        ControllerProfile.DUAL -> frame(0x27, channel, value)
        ControllerProfile.COMPACT -> null
    }

    fun direction(channel: Int, forward: Boolean): ByteArray? = when (profile) {
        ControllerProfile.FRAMED -> frame(0x56, forward.flag)
        ControllerProfile.DUAL -> frame(0x2a, channel, forward.flag)
        else -> null
    }

    fun play(playing: Boolean) = sceneFrame(0x5d, playing.flag)
    fun temperature(cold: Int, warm: Int) = sceneFrame(0x61, cold, warm)
    fun restore(mode: Int) = sceneFrame(0x0b, mode)
    fun coexist(enabled: Boolean) = sceneFrame(0x0a, enabled.flag)
    fun lightType(type: Int) = sceneFrame(0x6a, 1, type and 0x7f)
    fun scene(slot: Int) = if (profile == ControllerProfile.DUAL) frame(0x2e, slot) else null

    fun loop(mode: LightMode, enabled: Boolean): ByteArray? {
        val value = if (!enabled) 0 else if (mode == LightMode.MUSIC) 2 else 1
        return when (profile) {
            ControllerProfile.FRAMED -> frame(0x58, enabled.flag)
            ControllerProfile.A0 -> frame(0x6a, value)
            ControllerProfile.COMPACT -> frame(0x16, value)
            ControllerProfile.DUAL -> null
        }
    }

    fun audioInput(input: Int): ByteArray? = when (profile) {
        ControllerProfile.FRAMED -> frame(0x59, input)
        ControllerProfile.A0 -> frame(0x6c, input)
        ControllerProfile.COMPACT -> frame(0x19, input)
        ControllerProfile.DUAL -> null
    }

    fun order(channel: Int, order: Int): ByteArray? = when (profile) {
        ControllerProfile.FRAMED -> frame(0x6b, order)
        ControllerProfile.A0 -> frame(0x64, order)
        ControllerProfile.DUAL -> frame(0x24, channel, order)
        ControllerProfile.COMPACT -> null
    }

    private fun sceneFrame(op: Int, vararg data: Int) =
        if (profile == ControllerProfile.FRAMED) frame(op, *data) else null

    private fun frame(op: Int, vararg data: Int): ByteArray {
        val header = when (profile) {
            ControllerProfile.FRAMED -> intArrayOf(0x53, op, 0, 1, 0, data.size)
            ControllerProfile.A0 -> intArrayOf(0xa0, op, data.size)
            ControllerProfile.COMPACT -> intArrayOf(op, data.size)
            ControllerProfile.DUAL -> intArrayOf(0xaa, op, data.size)
        }
        return (header + data).map(Int::toByte).toByteArray()
    }
}

private val Boolean.flag get() = if (this) 1 else 0
internal fun ByteArray.u(index: Int) = this[index].toInt() and 255
