// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal object AudioReactiveEngine {
    @SuppressLint("MissingPermission")
    suspend fun captureMicrophone(onLevel: suspend (Float) -> Unit) {
        val sampleRate = 16_000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        capture(samplesPerRead = 1536, onLevel) {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, encoding, bufferSize(sampleRate, channel, 1536))
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    suspend fun capturePlayback(projection: MediaProjection, onLevel: suspend (Float) -> Unit) {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val rate = 48_000
        val channels = AudioFormat.CHANNEL_IN_STEREO
        val samplesPerRead = 7_200
        val format = AudioFormat.Builder()
            .setSampleRate(rate)
            .setChannelMask(channels)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        capture(samplesPerRead, onLevel) {
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize(rate, channels, samplesPerRead))
                .build()
        }
    }

    private fun bufferSize(rate: Int, channels: Int, samplesPerRead: Int): Int {
        val minimum = AudioRecord.getMinBufferSize(rate, channels, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        return maxOf(minimum, samplesPerRead * Short.SIZE_BYTES * 2)
    }

    private suspend fun capture(
        samplesPerRead: Int,
        onLevel: suspend (Float) -> Unit,
        openRecord: () -> AudioRecord
    ) = withContext(Dispatchers.IO) {
        val buffer = ShortArray(samplesPerRead)
        val record = openRecord()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED)
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            var smoothed = 0f
            while (currentCoroutineContext().isActive) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0)
                smoothed = audioLevel(buffer, count, smoothed)
                onLevel(smoothed)
                delay(75)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}

internal fun audioLevel(samples: ShortArray, count: Int, previous: Float): Float {
    var energy = 0.0
    for (index in 0 until count) {
        val sample = samples[index] / 32768.0
        energy += sample * sample
    }
    val rms = if (count == 0) 0f else (sqrt(energy / count) * 8).toFloat().coerceIn(0f, 1f)
    return maxOf(rms, previous * 0.65f)
}

internal fun audioBrightness(base: Int, level: Float): Int = (base * (0.15f + level * 0.85f)).toInt()
