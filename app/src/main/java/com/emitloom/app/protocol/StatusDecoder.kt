// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.protocol

import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.LightMode
import com.emitloom.app.model.LightState
import com.emitloom.app.model.LightType
import com.emitloom.app.model.PowerTransition

class StatusDecoder(private val profile: ControllerProfile) {
    private var buffered = byteArrayOf()
    private var expected = 0
    private var nextPacket = 1

    fun accept(packet: ByteArray): List<LightState>? {
        if (profile == ControllerProfile.FRAMED) {
            if (packet.size < 53 || packet.u(0) != 0x53 || packet.u(1) != 2 ||
                packet.u(2) != 0 || packet.u(5) != packet.size - 6
            ) return null
            val mode = LightMode.entries.find { it.wireId == packet.u(32) } ?: return null
            if (LightType.fromWire(packet.u(19)) == null) return null
            return listOf(
                LightState(
                    power = packet.u(29) != 0, brightness = packet.u(35),
                    color = packet.rgb(if (mode == LightMode.COLOR || mode == LightMode.WHITE) 37 else 47),
                    mode = mode, effect = packet.u(33), speed = packet.u(42), sensitivity = packet.u(45),
                    length = packet.u(43), forward = packet.u(44) != 0, playing = packet.u(34) != 0,
                    loop = packet.u(30) != 0, input = packet.u(46), white = packet.u(36),
                    cold = packet.u(40), warm = packet.u(41), lightType = packet.u(19), order = packet.u(31),
                    firmware = packet.copyOfRange(11, 18).decodeToString().trim { it <= ' ' },
                    powerRestore = packet.u(25), coexist = packet.u(24) != 0,
                    powerTransition = PowerTransition.fromWire(packet.u(20), packet.u(21), packet.u(22) * 256 + packet.u(23))
                )
            )
        }

        val payload = collect(packet) ?: return null
        return when (profile) {
            ControllerProfile.A0 -> if (payload.size >= 12) listOf(a0(payload)) else null
            ControllerProfile.COMPACT -> if (payload.size >= 13) listOf(compact(payload)) else null
            ControllerProfile.DUAL -> if (payload.size >= 22) List(2) { dual(payload, it * 11) } else null
            ControllerProfile.FRAMED -> null
        }
    }

    private fun collect(packet: ByteArray): ByteArray? {
        val compact = profile == ControllerProfile.COMPACT
        val offset = if (compact) 0 else 2
        if (packet.size <= offset + 1) return reset()
        if (!compact && (packet.u(0) != 0x53 || packet.u(1) != 0x43)) return reset()
        val part = packet.u(offset)
        val first = part == 1
        val header = offset + if (compact && !first) 2 else 3
        if (packet.size <= header) return reset()
        val length = packet.u(header - 1)
        if (packet.size != header + length) return reset()
        if (first) {
            reset()
            expected = packet.u(offset + 1)
        }
        if (part != nextPacket || (!compact && packet.u(offset + 1) != expected) ||
            buffered.size + length > expected
        ) return reset()
        buffered += packet.copyOfRange(header, packet.size)
        nextPacket++
        return if (buffered.size == expected) buffered.also { reset() } else null
    }

    private fun reset(): ByteArray? {
        buffered = byteArrayOf()
        expected = 0
        nextPacket = 1
        return null
    }

    private fun a0(data: ByteArray) = LightState(
        power = data.u(0) != 0, brightness = data.u(4), color = data.rgb(7),
        mode = mode(data.u(2), 0xbf), effect = data.u(2), speed = data.u(5), sensitivity = data.u(11),
        length = data.u(6), loop = data.u(1) != 0, input = data.u(10), order = data.u(3),
        white = data.u(data.size - 2)
    )

    private fun compact(data: ByteArray) = LightState(
        power = data.u(0) != 0, brightness = data.u(1), color = data.rgb(6),
        mode = mode(data.u(4), 0xcc), effect = data.u(4), speed = data.u(2), sensitivity = data.u(9),
        loop = data.u(5) != 0, input = data.u(data.size - 3), order = data.u(3), white = data.u(data.size - 2)
    )

    private fun dual(data: ByteArray, start: Int) = LightState(
        power = data.u(start) != 0, brightness = data.u(start + 3), color = data.rgb(start + 7),
        mode = mode(data.u(start + 1), -1), effect = data.u(start + 1),
        speed = data.u(start + 4), sensitivity = data.u(start + 10),
        length = data.u(start + 5), forward = data.u(start + 6) != 0, order = data.u(start + 2)
    )

    private fun mode(effect: Int, white: Int): LightMode = when {
        effect == profile.solidEffect -> LightMode.COLOR
        effect == white -> LightMode.WHITE
        profile == ControllerProfile.COMPACT && effect == 0x64 -> LightMode.CUSTOM
        effect >= profile.firstSound -> LightMode.MUSIC
        else -> LightMode.EFFECTS
    }

    private fun ByteArray.rgb(start: Int) = ColorRgb(u(start), u(start + 1), u(start + 2))
}
