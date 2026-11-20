// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app

import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.ControllerDevice
import com.emitloom.app.model.LightMode
import com.emitloom.app.model.LightState
import com.emitloom.app.model.effects
import com.emitloom.app.protocol.ControllerProfile
import com.emitloom.app.protocol.ControllerProtocol
import com.emitloom.app.protocol.StatusDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerProtocolTest {
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()

    @Test
    fun sp630PowerAndColorUseAConstantSixByteHeader() {
        val protocol = ControllerProtocol(ControllerProfile.FRAMED)
        assertArrayEquals(bytes(0x53, 0x50, 0, 1, 0, 1, 1), protocol.power(0, true))
        assertArrayEquals(
            bytes(0x53, 0x52, 0, 1, 0, 4, 255, 128, 0, 42),
            protocol.color(0, ColorRgb(255, 128, 0), 42).last()
        )
    }

    @Test
    fun sameGattServiceDoesNotImplySameProtocol() {
        assertNull(ControllerProfile.identify("Unknown UART", null))
        assertEquals(ControllerProfile.A0, ControllerProfile.identify("SP611E bedroom", null))
        assertEquals(ControllerProfile.COMPACT, ControllerProfile.identify("SP614E", null))
        assertEquals(ControllerProfile.DUAL, ControllerProfile.identify("SP601E", null))
        assertEquals(ControllerProfile.FRAMED, ControllerProfile.identify("Renamed", bytes(0x1f, 0x10)))
        assertArrayEquals(bytes(0xA0, 0x62, 1, 0), ControllerProtocol(ControllerProfile.A0).power(0, false))
        assertArrayEquals(bytes(0x0F, 1, 0), ControllerProtocol(ControllerProfile.COMPACT).power(0, false))
        assertArrayEquals(bytes(0xAA, 0x22, 2, 1, 0), ControllerProtocol(ControllerProfile.DUAL).power(1, false))
    }

    @Test
    fun fragmentedNotificationsValidateLengthAndChannelState() {
        val decoder = StatusDecoder(ControllerProfile.A0)
        assertNull(decoder.accept(bytes(0x53, 0x43, 1, 12, 8, 1, 0, 0xBE, 2, 127, 5, 30, 40)))
        assertNull(decoder.accept(bytes(0x53, 0x43, 3, 12, 4, 50, 60, 0, 16)))
        assertNull(decoder.accept(bytes(0x53, 0x43, 2, 12, 4, 50, 60, 0, 16)))
        assertNull(decoder.accept(bytes(0x53, 0x43, 1, 12, 8, 1, 0, 0xBE, 2, 127, 5, 30, 40)))
        val state = decoder.accept(bytes(0x53, 0x43, 2, 12, 4, 50, 60, 0, 16))!!.single()
        assertEquals(true, state.power)
        assertEquals(LightMode.COLOR, state.mode)
        assertEquals(ColorRgb(40, 50, 60), state.color)
        assertEquals(127, state.brightness)
    }

    @Test
    fun sceneStatusRejectsShortOrEncryptedFrames() {
        val decoder = StatusDecoder(ControllerProfile.FRAMED)
        assertNull(decoder.accept(bytes(0x53, 2, 0, 1, 0, 1, 1)))
        val status = ByteArray(53)
        bytes(0x53, 2, 0, 1, 0, 47).copyInto(status)
        status[19] = 0x86.toByte()
        status[29] = 1
        status[32] = 1
        status[35] = 200.toByte()
        status[37] = 12
        status[38] = 34
        status[39] = 56
        val state = decoder.accept(status)!!.single()
        assertEquals(ColorRgb(12, 34, 56), state.color)
        assertEquals(200, state.brightness)
        status[2] = 1
        assertNull(decoder.accept(status))
    }

    @Test
    fun spiCatalogsKeepTheSameSharedSequenceAtDifferentWireOffsets() {
        val state = LightState(true, 150, ColorRgb(255, 0, 0), LightMode.EFFECTS, 1, 5, 8, lightType = 0x86)
        val scene = ControllerDevice("test", "SP630E", ControllerProfile.FRAMED).effects(state, LightMode.EFFECTS)
        val a0 = ControllerDevice("test", "SP611E", ControllerProfile.A0).effects(state, LightMode.EFFECTS)
        assertEquals((1..0x92).toList(), scene.map { it.id })
        assertEquals((1..0x8e).toList(), a0.map { it.id })
        assertEquals("Red Comet Spin", a0.single { it.id == 0x79 }.name)
        assertEquals("Red Comet Spin", scene.single { it.id == 0x7d }.name)
    }

    @Test
    fun compactStatusReportsCustomModeAcrossFragments() {
        val decoder = StatusDecoder(ControllerProfile.COMPACT)
        assertNull(decoder.accept(bytes(1, 13, 7, 1, 128, 5, 0, 0x64, 0, 25)))
        val state = decoder.accept(bytes(2, 6, 50, 75, 8, 0, 40, 0))!!.single()
        assertEquals(LightMode.CUSTOM, state.mode)
        assertEquals(ColorRgb(25, 50, 75), state.color)
    }
}
