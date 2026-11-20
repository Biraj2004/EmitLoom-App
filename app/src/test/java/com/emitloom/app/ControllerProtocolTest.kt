// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app

import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.ColorStep
import com.emitloom.app.model.PowerTransition
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
    @Test
    fun hexColorsRequireSixAsciiHexDigits() {
        assertEquals(ColorRgb(0xab, 0xcd, 0xef), ColorRgb.fromHex(" #aBcDeF "))
        assertNull(ColorRgb.fromHex("+12345"))
        assertNull(ColorRgb.fromHex("-00000"))
        assertNull(ColorRgb.fromHex("１２３４５６"))
        assertNull(ColorStep.parse("#+12345", "2"))
    }

    @Test
    fun framedWhiteBrightnessAndWarmthUseSeparateCommands() {
        val protocol = ControllerProtocol(ControllerProfile.FRAMED)
        val packets = protocol.white(channel = 0, level = 128, switchMode = true)
        assertEquals(2, packets.size)
        assertArrayEquals(bytes(0x53, 0x53, 0, 1, 0, 2, 2, 1), packets[0])
        assertArrayEquals(bytes(0x53, 0x51, 0, 1, 0, 2, 1, 128), packets[1])
        assertArrayEquals(bytes(0x53, 0x61, 0, 1, 0, 2, 255, 0), protocol.temperature(255, 0))
        assertNull(ControllerProtocol(ControllerProfile.DUAL).temperature(255, 0))
    }

    @Test
    fun dualOutputStatusAndWritesKeepChannelsIndependent() {
        val decoder = StatusDecoder(ControllerProfile.DUAL)
        val status = bytes(0x53, 0x43, 1, 23, 23) +
            bytes(1, 0x19, 0, 42, 4, 25, 1, 255, 0, 0, 6) +
            bytes(0, 0x19, 2, 191, 8, 30, 0, 0, 0, 255, 9) + bytes(0)
        val outputs = decoder.accept(status)!!
        assertEquals(2, outputs.size)
        assertEquals(listOf(true, false), outputs.map { it.power })
        assertEquals(listOf(42, 191), outputs.map { it.brightness })
        assertEquals(listOf(ColorRgb(255, 0, 0), ColorRgb(0, 0, 255)), outputs.map { it.color })
        val protocol = ControllerProtocol(ControllerProfile.DUAL)
        assertArrayEquals(bytes(0xAA, 0x25, 2, 0, 42), protocol.brightness(0, 42))
        assertArrayEquals(bytes(0xAA, 0x25, 2, 1, 191), protocol.brightness(1, 191))
    }

    @Test
    fun colorSequenceStepValidatesHexAndDuration() {
        assertEquals(ColorStep(ColorRgb(10, 20, 30), 120), ColorStep.parse("#0A141E", "120"))
        assertNull(ColorStep.parse("#0A141E", "121"))
        assertNull(ColorStep.parse("#BOGUS", "2"))
    }

    @Test
    fun powerTransitionEncodesPixelCountInBigEndian() {
        val setting = PowerTransition.fromWire(4, 3, 600)!!
        assertArrayEquals(bytes(0x53, 0x08, 0, 1, 0, 5, 1, 4, 3, 2, 88),
            ControllerProtocol(ControllerProfile.FRAMED).powerTransition(setting))
        assertNull(ControllerProtocol(ControllerProfile.COMPACT).powerTransition(setting))
        assertNull(PowerTransition.fromWire(1, 1, 601))
    }
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
