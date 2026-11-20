// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import com.emitloom.app.model.ControllerDevice
import com.emitloom.app.model.LightState
import com.emitloom.app.protocol.CCCD
import com.emitloom.app.protocol.ControllerProfile
import com.emitloom.app.protocol.ControllerProtocol
import com.emitloom.app.protocol.ALTERNATE_UART_SERVICE
import com.emitloom.app.protocol.CONTROLLER_COMPANY_ID
import com.emitloom.app.protocol.StatusDecoder
import com.emitloom.app.protocol.UART_CHARACTERISTIC
import com.emitloom.app.protocol.UART_SERVICE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanEntry(val device: ControllerDevice, val name: String, val signal: Int)

data class DiscoveryState(
    val scanning: Boolean = false,
    val devices: List<ScanEntry> = emptyList(),
    val error: String? = null
)

data class ConnectionState(
    val device: ControllerDevice? = null,
    val connecting: Boolean = false,
    val outputs: List<LightState> = emptyList(),
    val error: String? = null
)

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery = _discovery.asStateFlow()
    private val _connection = MutableStateFlow(ConnectionState())
    val connection = _connection.asStateFlow()
    private var scan: ScanCallback? = null
    private var scanTimeout: Job? = null
    private var session: Session? = null

    val available get() = adapter != null
    val enabled get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    private data class WriteBatch(val packets: List<ByteArray>, val key: String?)

    private class Session(val device: ControllerDevice, val gatt: BluetoothGatt) {
        val decoder = StatusDecoder(device.profile)
        val pending = ArrayDeque<WriteBatch>()
        var characteristic: BluetoothGattCharacteristic? = null
        var activePackets: Iterator<ByteArray>? = null
        var connectTimeout: Job? = null
        var writeTimeout: Job? = null
        var mtuTimeout: Job? = null
        var discovering = false
        var writing = false
        var statusRequested = false
    }

    fun startScan() {
        stopScan()
        val location = context.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R &&
            (location == null || !LocationManagerCompat.isLocationEnabled(location))) {
            _discovery.value = DiscoveryState(error = "Turn on Location to scan on this Android version.")
            return
        }
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
        if (scanner == null) {
            _discovery.value = DiscoveryState(error = "Bluetooth scanning is unavailable. Check Bluetooth and Nearby devices.")
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val source = this
                scope.launch { if (scan === source) addResult(result) }
            }

            override fun onScanFailed(errorCode: Int) {
                val source = this
                scope.launch {
                    if (scan !== source) return@launch
                    stopScan()
                    _discovery.update { it.copy(error = "Bluetooth scan failed ($errorCode). Try scanning again.") }
                }
            }
        }
        scan = callback
        _discovery.value = DiscoveryState(scanning = true)
        val started = runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        }.isSuccess
        if (!started) {
            stopScan()
            _discovery.update { it.copy(error = "Unable to scan. Check the Nearby devices permission.") }
            return
        }
        scanTimeout = scope.launch {
            delay(12_000)
            stopScan()
        }
    }

    fun stopScan() {
        scanTimeout?.cancel()
        scanTimeout = null
        val callback = scan
        scan = null
        if (callback != null) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _discovery.update { it.copy(scanning = false) }
    }

    private fun addResult(result: ScanResult) {
        if (!result.isConnectable) return
        val record = result.scanRecord ?: return
        val data = record.getManufacturerSpecificData(CONTROLLER_COMPANY_ID)
        val name = record.deviceName ?: runCatching { result.device.name }.getOrNull()
        val model = ControllerProfile.advertisedModel(name, data) ?: return
        val profile = ControllerProfile.identify(model, data) ?: return
        val device = ControllerDevice(result.device.address, model, profile)
        val item = ScanEntry(device, name?.takeIf(String::isNotBlank) ?: model, result.rssi)
        _discovery.update { state ->
            state.copy(devices = (state.devices.filterNot { it.device.address == device.address } + item)
                .sortedWith(compareByDescending<ScanEntry> { it.signal }.thenBy { it.device.address }))
        }
    }

    fun connect(device: ControllerDevice) {
        stopScan()
        disconnect()
        _connection.value = ConnectionState(device = device, connecting = true)
        val gatt = runCatching {
            adapter?.getRemoteDevice(device.address)?.connectGatt(
                context, false, callback, BluetoothDevice.TRANSPORT_LE
            )
        }.getOrNull()
        if (gatt == null) {
            fail("Could not start the Bluetooth connection.")
            return
        }
        val current = Session(device, gatt)
        session = current
        current.connectTimeout = scope.launch {
            delay(18_000)
            if (session === current) fail("The controller did not return its status. Move closer and try again.")
        }
    }

    fun disconnect() {
        closeSession()
        _connection.value = ConnectionState()
    }

    fun close() {
        stopScan()
        disconnect()
        scope.cancel()
    }

    private fun fail(message: String) {
        val device = _connection.value.device
        closeSession()
        _connection.value = ConnectionState(device = device, error = message)
    }

    private fun closeSession() {
        val current = session ?: return
        session = null
        current.connectTimeout?.cancel()
        current.writeTimeout?.cancel()
        current.mtuTimeout?.cancel()
        runCatching { current.gatt.disconnect() }
        runCatching { current.gatt.close() }
    }

    private fun onGatt(gatt: BluetoothGatt, action: (Session) -> Unit) {
        scope.launch {
            val current = session?.takeIf { it.gatt === gatt } ?: return@launch
            try {
                action(current)
            } catch (_: SecurityException) {
                fail("Bluetooth permission changed. Allow Nearby devices and reconnect.")
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onGatt(gatt) { current ->
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail(if (status == BluetoothGatt.GATT_SUCCESS) "The controller disconnected."
                    else "Bluetooth connection failed ($status). Try reconnecting.")
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (gatt.requestMtu(128)) {
                        current.mtuTimeout = scope.launch {
                            delay(2_000)
                            if (session === current) discover(current)
                        }
                    } else {
                        discover(current)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            onGatt(gatt) { current ->
                current.mtuTimeout?.cancel()
                discover(current)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            onGatt(gatt) { current ->
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Could not read controller services ($status).")
                    return@onGatt
                }
                subscribe(current)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            onGatt(gatt) { current ->
                if (descriptor.uuid != CCCD || current.statusRequested) return@onGatt
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("The controller rejected status notifications ($status).")
                    return@onGatt
                }
                current.statusRequested = true
                enqueue(current, listOf(ControllerProtocol(current.device.profile).status()), null)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            onGatt(gatt) { current ->
                if (current.characteristic != characteristic || !current.writing) return@onGatt
                current.writeTimeout?.cancel()
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("The controller rejected a command ($status). Reconnect to refresh its state.")
                    return@onGatt
                }
                current.writing = false
                writeNext(current)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            onGatt(gatt) { acceptStatus(it, value) }
        }

        @Deprecated("Compatibility callback for Android 12 and earlier")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            val value = characteristic.value?.copyOf() ?: return
            onGatt(gatt) { acceptStatus(it, value) }
        }
    }

    private fun discover(current: Session) {
        if (current.discovering) return
        current.discovering = true
        if (!runCatching { current.gatt.discoverServices() }.getOrDefault(false)) {
            fail("Could not start service discovery.")
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribe(current: Session) {
        val services = if (current.device.profile == ControllerProfile.FRAMED) listOf(ALTERNATE_UART_SERVICE, UART_SERVICE)
        else listOf(UART_SERVICE)
        val target = services.firstNotNullOfOrNull { id ->
            current.gatt.getService(id)?.getCharacteristic(UART_CHARACTERISTIC)?.takeIf { it.canWrite() }
        }
        val service = target?.service
        val notify = target?.takeIf { it.canNotify() } ?: service?.characteristics?.firstOrNull { it.canNotify() }
        val descriptor = notify?.getDescriptor(CCCD)
        if (target == null || notify == null || descriptor == null ||
            !current.gatt.setCharacteristicNotification(notify, true)
        ) {
            fail("This controller did not expose the expected Bluetooth controls.")
            return
        }

        current.characteristic = target
        val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        val started = if (Build.VERSION.SDK_INT >= 33) {
            current.gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            current.gatt.writeDescriptor(descriptor)
        }
        if (!started) fail("Could not enable controller status notifications.")
    }

    private fun acceptStatus(current: Session, value: ByteArray) {
        if (!current.statusRequested) return
        val outputs = current.decoder.accept(value) ?: return
        current.connectTimeout?.cancel()
        _connection.value = ConnectionState(device = current.device, outputs = outputs)
    }

    fun send(packets: List<ByteArray>, key: String? = null): Boolean {
        val current = session ?: return false
        if (_connection.value.outputs.isEmpty() || packets.isEmpty()) return false
        return enqueue(current, packets, key)
    }

    private fun enqueue(current: Session, packets: List<ByteArray>, key: String?): Boolean {
        if (key != null && current.pending.lastOrNull()?.key == key) current.pending.removeLast()
        if (current.pending.size >= 64) {
            fail("The Bluetooth command queue stopped responding. Reconnect and try again.")
            return false
        }
        current.pending.addLast(WriteBatch(packets, key))
        return writeNext(current)
    }

    @Suppress("DEPRECATION")
    private fun writeNext(current: Session): Boolean {
        if (current.writing) return true
        if (current.activePackets?.hasNext() != true) {
            current.activePackets = current.pending.removeFirstOrNull()?.packets?.iterator() ?: return true
        }
        val packet = current.activePackets?.next() ?: return false
        val characteristic = current.characteristic ?: return false
        val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                current.gatt.writeCharacteristic(characteristic, packet, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = writeType
                characteristic.value = packet
                current.gatt.writeCharacteristic(characteristic)
            }
        }.getOrDefault(false)
        if (!started) {
            fail("Android could not send a Bluetooth command. Reconnect and try again.")
            return false
        }
        current.writing = true
        current.writeTimeout = scope.launch {
            delay(4_000)
            if (session === current && current.writing) fail("Bluetooth stopped responding. Reconnect and try again.")
        }
        return true
    }

    private fun BluetoothGattCharacteristic.canWrite() =
        properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    private fun BluetoothGattCharacteristic.canNotify() =
        properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
}
