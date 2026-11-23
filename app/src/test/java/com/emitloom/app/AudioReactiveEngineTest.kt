// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app

import com.emitloom.app.audio.audioBrightness
import com.emitloom.app.audio.audioLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioReactiveEngineTest {
    @Test
    fun levelUsesOnlySamplesReadAndFadesWhenPlaybackStops() {
        val samples = shortArrayOf(2_048, -2_048, Short.MAX_VALUE)
        val level = audioLevel(samples, count = 2, previous = 0f)
        assertEquals(0.5f, level, 0.001f)
        assertEquals(0.325f, audioLevel(samples, count = 0, previous = level), 0.001f)
        assertEquals(1f, audioLevel(samples, count = 3, previous = 0f), 0.001f)
    }

    @Test
    fun syncDoesNotExceedSelectedBrightness() {
        assertEquals(30, audioBrightness(base = 200, level = 0f))
        assertEquals(200, audioBrightness(base = 200, level = 1f))
        assertEquals(0, audioBrightness(base = 0, level = 1f))
    }
}
