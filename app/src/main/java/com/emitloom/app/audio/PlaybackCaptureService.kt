// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@RequiresApi(Build.VERSION_CODES.Q)
internal class PlaybackCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var receiver: ResultReceiver? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() = finishCapture()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_CAPTURE) {
            finishCapture()
            return START_NOT_STICKY
        }
        if (receiver != null) return START_NOT_STICKY
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val request = IntentCompat.getParcelableExtra(intent, EXTRA_RECEIVER, ResultReceiver::class.java)
        receiver = request

        try {
            val consent = checkNotNull(IntentCompat.getParcelableExtra(intent, EXTRA_CONSENT, Intent::class.java))
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            request?.send(FOREGROUND, null)
            val manager = getSystemService(MediaProjectionManager::class.java)
            val session = checkNotNull(manager.getMediaProjection(Activity.RESULT_OK, consent))
            projection = session
            session.registerCallback(projectionCallback, mainHandler)
            scope.launch {
                try {
                    AudioReactiveEngine.capturePlayback(session) { level ->
                        request?.send(LEVEL, Bundle().apply { putFloat(AUDIO_LEVEL, level) })
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    finishCapture(FAILED)
                }
            }
        } catch (_: Exception) {
            finishCapture(FAILED)
        }
        return START_NOT_STICKY
    }

    private fun finishCapture(result: Int = STOPPED) {
        receiver?.send(result, null)
        receiver = null
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        receiver?.send(STOPPED, null)
        receiver = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "Now Playing sync", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(this, 0,
            Intent(this, PlaybackCaptureService::class.java).setAction(STOP_CAPTURE), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("EmitLoom Now Playing sync")
            .setContentText("Media audio controls the light brightness.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop sync", stop).build())
            .build()
    }

    companion object {
        private const val EXTRA_CONSENT = "projectionConsent"
        private const val EXTRA_RECEIVER = "captureReceiver"
        private const val AUDIO_LEVEL = "audioLevel"
        private const val LEVEL = 1
        private const val STOPPED = 2
        private const val FAILED = 3
        private const val FOREGROUND = 4
        private const val STOP_CAPTURE = "com.emitloom.app.STOP_PLAYBACK_CAPTURE"
        private const val NOTIFICATION_CHANNEL = "playback_sync"
        private const val NOTIFICATION_ID = 41
        private val captureLock = Mutex()

        suspend fun capture(context: Context, consent: Intent, onLevel: suspend (Float) -> Unit) {
            captureLock.withLock {
                val service = Intent(context, PlaybackCaptureService::class.java)
                    .putExtra(EXTRA_CONSENT, consent)
                val foreground = CompletableDeferred<Unit>()
                var launched = false
                try {
                    callbackFlow {
                        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                            override fun onReceiveResult(resultCode: Int, data: Bundle?) {
                                when (resultCode) {
                                    LEVEL -> data?.let { trySend(it.getFloat(AUDIO_LEVEL)) }
                                    FOREGROUND -> foreground.complete(Unit)
                                    FAILED -> {
                                        foreground.complete(Unit)
                                        close(IllegalStateException("Playback audio capture failed"))
                                    }
                                    STOPPED -> {
                                        foreground.complete(Unit)
                                        close()
                                    }
                                }
                            }
                        }
                        ContextCompat.startForegroundService(context, service.putExtra(EXTRA_RECEIVER, receiver))
                        launched = true
                        awaitClose()
                    }.conflate().collect { onLevel(it) }
                } finally {
                    if (launched) withContext(NonCancellable) {
                        // A startForegroundService request must be promoted even if Stop is tapped during startup.
                        withTimeoutOrNull(5_000) { foreground.await() }
                        context.stopService(service)
                    }
                }
            }
        }
    }
}
