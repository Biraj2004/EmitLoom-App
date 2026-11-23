// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emitloom.app.ui.EmitLoomApp
import com.emitloom.app.ui.EmitLoomTheme
import com.emitloom.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(application) } }
    }
    private var bluetoothAction: (() -> Unit)? = null

    private val nearbyPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (bluetoothPermissions().all(::hasPermission)) ensureBluetoothEnabled()
        else {
            bluetoothAction = null
            model.showMessage("Allow Nearby devices (or Location on older Android versions) to find and connect controllers.")
        }
    }

    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = bluetoothAction
        bluetoothAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
        else model.showMessage("Turn on Bluetooth to connect a controller.")
    }

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.startPhoneMic()
        else model.showMessage("Microphone permission is required for phone sound sync.")
    }

    private val playbackPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestPlaybackCapture()
        else model.showMessage("Allow microphone access in Android to enable playback audio capture.")
    }

    private val playbackConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val consent = result.data
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && result.resultCode == Activity.RESULT_OK && consent != null) {
            model.startNowPlaying(consent)
        } else model.showMessage("Now Playing sync requires Android's capture approval.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            EmitLoomTheme {
                EmitLoomApp(
                    model = model,
                    onScan = { withBluetooth(model::scan) },
                    onConnect = { controller -> withBluetooth { model.connect(controller) } },
                    onPhoneMic = {
                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) model.startPhoneMic()
                        else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onNowPlaying = {
                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) requestPlaybackCapture()
                        else playbackPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }

    override fun onStop() {
        model.stopSequence()
        model.stopScan()
        model.stopAudioSync()
        super.onStop()
    }

    private fun requestPlaybackCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else manager.createScreenCaptureIntent()
            playbackConsent.launch(intent)
        } catch (_: RuntimeException) {
            model.showMessage("Android could not open the Now Playing capture prompt.")
        }
    }

    private fun withBluetooth(action: () -> Unit) {
        if (!model.bluetoothAvailable) {
            model.showMessage("This Android device has no Bluetooth adapter.")
            return
        }
        bluetoothAction = action
        val missing = bluetoothPermissions().filterNot(::hasPermission)
        if (missing.isEmpty()) ensureBluetoothEnabled()
        else nearbyPermission.launch(missing.toTypedArray())
    }

    private fun ensureBluetoothEnabled() {
        if (model.bluetoothEnabled) {
            val action = bluetoothAction
            bluetoothAction = null
            action?.invoke()
        } else {
            val started = runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }.isSuccess
            if (!started) {
                bluetoothAction = null
                model.showMessage("Turn on Bluetooth in Android settings and try again.")
            }
        }
    }

    private fun bluetoothPermissions() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
