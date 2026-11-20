// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

object AudioReactiveEngine {
    @SuppressLint("MissingPermission")
    suspend fun capture(onLevel: suspend (Float) -> Unit) = withContext(Dispatchers.IO) {
        val sampleRate = 16_000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        check(minimum > 0)
        val buffer = ShortArray(1536)
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, encoding, maxOf(minimum, 4096))
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED)
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            var smoothed = 0f
            while (currentCoroutineContext().isActive) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0)
                if (count > 0) {
                    var energy = 0.0
                    for (index in 0 until count) {
                        val sample = buffer[index] / 32768.0
                        energy += sample * sample
                    }
                    val rms = (sqrt(energy / count) * 8).toFloat().coerceIn(0f, 1f)
                    smoothed = maxOf(rms, smoothed * 0.65f)
                    onLevel(smoothed)
                }
                delay(75)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}
