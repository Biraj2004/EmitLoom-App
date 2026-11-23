// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emitloom.app.BuildConfig
import com.emitloom.app.audio.AudioReactiveEngine
import com.emitloom.app.audio.PlaybackCaptureService
import com.emitloom.app.audio.audioBrightness
import com.emitloom.app.bluetooth.BleManager
import com.emitloom.app.bluetooth.ScanEntry
import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.ColorSequence
import com.emitloom.app.model.ColorStep
import com.emitloom.app.model.ControllerDevice
import com.emitloom.app.model.LightMode
import com.emitloom.app.model.LightState
import com.emitloom.app.model.LightType
import com.emitloom.app.model.LocalLibrary
import com.emitloom.app.model.PowerTransition
import com.emitloom.app.model.SavedController
import com.emitloom.app.model.SavedLook
import com.emitloom.app.model.effects
import com.emitloom.app.model.hasRgb
import com.emitloom.app.model.hasWhite
import com.emitloom.app.model.lightType
import com.emitloom.app.protocol.ControllerProfile
import com.emitloom.app.protocol.ControllerProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class DeviceControls(
    val device: ControllerDevice,
    val outputs: List<LightState>,
    val channel: Int = 0,
    val demoName: String? = null
) {
    val light: LightState get() = outputs[channel]
}

internal const val MAX_POWER_TIMERS = 5

data class ActiveTimer(val id: Long, val deadline: Long, val turnOn: Boolean, val channel: Int)

enum class AudioSyncSource(val title: String) { MICROPHONE("Phone microphone"), PLAYBACK("Now Playing") }

data class AudioSync(val source: AudioSyncSource, val level: Float? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val ble = BleManager(application)
    private val library = LocalLibrary(application)
    val discovery = ble.discovery
    val connection = ble.connection
    val bluetoothAvailable get() = ble.available
    val bluetoothEnabled get() = ble.enabled

    private val _controllers = MutableStateFlow(library.controllers())
    val controllers = _controllers.asStateFlow()
    private val _controllerGrid = MutableStateFlow(library.usesControllerGrid())
    val controllerGrid = _controllerGrid.asStateFlow()
    private val _looks = MutableStateFlow(library.looks())
    val looks = _looks.asStateFlow()
    private val _sequences = MutableStateFlow(library.sequences())
    val sequences = _sequences.asStateFlow()
    private val _playingSequence = MutableStateFlow<String?>(null)
    val playingSequence = _playingSequence.asStateFlow()
    private var sequenceJob: Job? = null
    private val _controls = MutableStateFlow<DeviceControls?>(null)
    val controls = _controls.asStateFlow()
    private val _timers = MutableStateFlow<List<ActiveTimer>>(emptyList())
    val timers = _timers.asStateFlow()
    private val _audioSync = MutableStateFlow<AudioSync?>(null)
    val audioSync = _audioSync.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var audioJob: Job? = null
    private val timerJobs = mutableMapOf<Long, Job>()
    private var nextTimerId = 0L
    private var nameOnConnect: String? = null

    init {
        viewModelScope.launch {
            connection.collect { value ->
                if (_controls.value?.demoName != null) return@collect
                val device = value.device
                if (device == null || value.outputs.isEmpty()) {
                    _controls.value = null
                    stopSequence()
                    stopAudioSync(restore = false)
                    cancelTimers()
                    return@collect
                }
                val current = _controls.value
                val channel = if (current?.device == device) current.channel else 0
                val selected = channel.coerceIn(value.outputs.indices)
                val outputs = if (_audioSync.value != null && current?.device == device && channel == selected) {
                    value.outputs.toMutableList().apply {
                        this[selected] = this[selected].copy(brightness = current.light.brightness)
                    }
                } else value.outputs
                _controls.value = DeviceControls(device, outputs, selected)
                if (_controllers.value.none { it.device.address == device.address }) {
                    updateControllers(_controllers.value + SavedController(device, nameOnConnect ?: device.model))
                }
                nameOnConnect = null
            }
        }
    }

    fun scan() = ble.startScan()
    fun setControllerGrid(enabled: Boolean) {
        if (_controllerGrid.value == enabled) return
        _controllerGrid.value = enabled
        library.storeControllerGrid(enabled)
    }

    fun stopScan() = ble.stopScan()

    fun connectDemo() {
        if (!BuildConfig.DEMO_CONTROLLER || _controls.value?.demoName != null) return
        stopScan()
        disconnect()
        _looks.value = emptyList()
        _sequences.value = emptyList()
        _controls.value = demoController()
    }

    fun connect(entry: ScanEntry) = connect(SavedController(entry.device, entry.name))

    fun connect(saved: SavedController) {
        if (connection.value.device?.address == saved.device.address &&
            (connection.value.connecting || _controls.value != null)
        ) return
        disconnect()
        nameOnConnect = saved.name
        ble.connect(saved.device)
    }

    fun disconnect() {
        val wasDemo = _controls.value?.demoName != null
        stopSequence()
        stopAudioSync(restore = false)
        cancelTimers()
        _controls.value = null
        if (wasDemo) {
            _looks.value = library.looks()
            _sequences.value = library.sequences()
        }
        nameOnConnect = null
        ble.disconnect()
    }

    fun rename(address: String, name: String) {
        val label = name.trim().take(40)
        if (label.isEmpty()) return
        val current = _controls.value
        if (current?.demoName != null && current.device.address == address) {
            _controls.value = current.copy(demoName = label)
            return
        }
        updateControllers(_controllers.value.map { if (it.device.address == address) it.copy(name = label) else it })
    }

    fun forget(address: String) {
        if (_controls.value?.let { it.demoName != null && it.device.address == address } == true) {
            disconnect()
            return
        }
        if (connection.value.device?.address == address) disconnect()
        updateControllers(_controllers.value.filterNot { it.device.address == address })
        updateLooks(_looks.value.filterNot { it.address == address })
    }

    fun selectChannel(channel: Int) {
        val current = _controls.value ?: return
        if (channel !in current.outputs.indices || channel == current.channel) return
        stopSequence()
        stopAudioSync()
        _controls.value = current.copy(channel = channel)
    }

    fun power(on: Boolean) {
        if (!on) stopAudioSync()
        change { protocol, current ->
            listOf(protocol.power(current.channel, on)) to current.light.copy(power = on)
        }
    }

    fun setColor(color: ColorRgb) {
        change("color") { protocol, current ->
            val light = current.light
            protocol.color(current.channel, color, light.brightness, light.mode != LightMode.COLOR) to
                light.copy(color = color, mode = LightMode.COLOR, effect = protocol.profile.solidEffect)
        }
    }

    fun solidBrightness(value: Int) {
        val level = value.coerceIn(0, 255)
        change("brightness") { protocol, current ->
            val light = current.light
            val channel = current.channel
            val packets = if (light.mode == LightMode.COLOR) {
                listOf(protocol.brightness(channel, level))
            } else {
                protocol.color(channel, light.color, level) +
                    listOfNotNull(protocol.brightness(channel, level).takeIf { protocol.profile == ControllerProfile.DUAL })
            }
            packets to light.copy(brightness = level, mode = LightMode.COLOR, effect = protocol.profile.solidEffect)
        }
    }

    fun brightness(value: Int, whiteEffect: Boolean = false) {
        val level = value.coerceIn(0, 255)
        change("brightness") { protocol, current ->
            listOf(protocol.brightness(current.channel, level, whiteEffect)) to
                if (whiteEffect) current.light.copy(white = level) else current.light.copy(brightness = level)
        }
    }

    fun white(value: Int) {
        stopAudioSync()
        val level = value.coerceIn(0, 255)
        change("white") { protocol, current ->
            protocol.white(current.channel, level, current.light.mode != LightMode.WHITE) to
                current.light.copy(white = level, mode = LightMode.WHITE)
        }
    }

    fun temperature(warm: Int) {
        stopAudioSync()
        val level = warm.coerceIn(0, 255)
        change("temperature") { protocol, current ->
            val packets = buildList {
                if (current.light.mode != LightMode.WHITE) {
                    addAll(protocol.white(current.channel, current.light.white, switchMode = true))
                }
                protocol.temperature(255 - level, level)?.let(::add)
            }
            packets to current.light.copy(mode = LightMode.WHITE, cold = 255 - level, warm = level)
        }
    }

    fun effect(mode: LightMode, id: Int) {
        stopAudioSync()
        change { protocol, current ->
            listOf(protocol.effect(current.channel, mode, id)) to current.light.copy(mode = mode, effect = id)
        }
    }

    fun speed(value: Int) = change("speed") { protocol, current ->
        listOf(protocol.speed(current.channel, value)) to current.light.copy(speed = value)
    }

    fun length(value: Int) = change("length") { protocol, current ->
        listOfNotNull(protocol.length(current.channel, value)) to current.light.copy(length = value)
    }

    fun sensitivity(value: Int) = change("sensitivity") { protocol, current ->
        val gain = value.coerceIn(1, if (protocol.profile == ControllerProfile.COMPACT) 15 else 16)
        listOf(protocol.sensitivity(current.channel, gain)) to current.light.copy(sensitivity = gain)
    }

    fun play(playing: Boolean) = change { protocol, current ->
        listOfNotNull(protocol.play(playing)) to current.light.copy(playing = playing)
    }

    fun loop(enabled: Boolean) = change { protocol, current ->
        listOfNotNull(protocol.loop(current.light.mode, enabled)) to current.light.copy(loop = enabled)
    }

    fun direction(forward: Boolean) = change { protocol, current ->
        listOfNotNull(protocol.direction(current.channel, forward)) to current.light.copy(forward = forward)
    }

    fun audioInput(input: Int) = change { protocol, current ->
        listOfNotNull(protocol.audioInput(input)) to current.light.copy(input = input)
    }

    fun colorOrder(order: Int) = change { protocol, current ->
        listOfNotNull(protocol.order(current.channel, order)) to current.light.copy(order = order)
    }

    fun lightType(type: LightType) {
        stopAudioSync()
        change { protocol, current ->
            val mode = if (type.rgb) LightMode.COLOR else LightMode.WHITE
            val commands = buildList {
                if (current.light.power) add(protocol.power(current.channel, false))
                protocol.lightType(type.id)?.let(::add)
                protocol.order(current.channel, 0)?.let(::add)
                add(protocol.effect(current.channel, mode, 1))
                add(protocol.status())
            }
            commands to current.light.copy(power = false, lightType = type.id, order = 0, mode = mode, effect = 1)
        }
    }

    fun powerRestore(mode: Int) = change { protocol, current ->
        listOfNotNull(protocol.restore(mode)) to current.light.copy(powerRestore = mode)
    }

    fun powerTransition(setting: PowerTransition) = change { protocol, current ->
        listOfNotNull(protocol.powerTransition(setting)) to current.light.copy(powerTransition = setting)
    }

    fun coexist(enabled: Boolean) = change { protocol, current ->
        listOfNotNull(protocol.coexist(enabled)) to current.light.copy(coexist = enabled)
    }

    fun recallSlot(slot: Int) {
        val current = _controls.value ?: return
        val protocol = ControllerProtocol(current.device.profile)
        val scene = protocol.scene(slot) ?: return
        stopSequence()
        stopAudioSync()
        send(current, listOf(scene, protocol.status()))
    }

    fun refresh() {
        val current = _controls.value ?: return
        stopSequence()
        stopAudioSync()
        send(current, listOf(ControllerProtocol(current.device.profile).status()))
        if (current.demoName != null) showMessage("Demo controller status is current.")
    }

    fun saveLook(name: String) {
        val current = _controls.value ?: return
        val label = name.trim().take(40)
        if (label.isEmpty()) return
        val look = SavedLook(UUID.randomUUID().toString(), label, current.device.address, current.channel, current.light)
        updateLooks(_looks.value + look)
    }

    fun deleteLook(id: String) = updateLooks(_looks.value.filterNot { it.id == id })

    fun applyLook(look: SavedLook) {
        val current = _controls.value ?: return
        if (look.address != current.device.address || look.channel !in current.outputs.indices) return
        val saved = look.state
        val target = current.outputs[look.channel]
        val compatible = when (saved.mode) {
            LightMode.COLOR -> current.device.hasRgb(target)
            LightMode.WHITE -> current.device.hasWhite(target)
            LightMode.WHITE_EFFECTS, LightMode.WHITE_MUSIC ->
                current.device.hasWhite(target) && current.device.effects(target, saved.mode).any { it.id == saved.effect }
            else -> current.device.hasRgb(target) && current.device.effects(target, saved.mode).any { it.id == saved.effect }
        }
        if (!compatible || (current.device.profile == ControllerProfile.FRAMED &&
                (saved.lightType and 0x7f) != (target.lightType and 0x7f))
        ) {
            _message.value = "This look uses a different output configuration."
            return
        }
        selectChannel(look.channel)
        stopAudioSync()
        change { p, c ->
            val channel = c.channel
            val packets = buildList {
                add(p.power(channel, true))
                when (saved.mode) {
                    LightMode.COLOR -> {
                        addAll(p.color(channel, saved.color, saved.brightness))
                        if (p.profile == ControllerProfile.DUAL) add(p.brightness(channel, saved.brightness))
                    }
                    LightMode.WHITE -> {
                        addAll(p.white(channel, saved.white, switchMode = true))
                        if (c.device.lightType(c.light)?.cct == true) p.temperature(saved.cold, saved.warm)?.let(::add)
                    }
                    LightMode.EFFECTS, LightMode.WHITE_EFFECTS -> {
                        add(p.effect(channel, saved.mode, saved.effect))
                        add(p.speed(channel, saved.speed.coerceIn(1, 10)))
                        val white = saved.mode == LightMode.WHITE_EFFECTS
                        add(p.brightness(channel, if (white) saved.white else saved.brightness, white))
                    }
                    LightMode.MUSIC, LightMode.WHITE_MUSIC -> {
                        add(p.effect(channel, saved.mode, saved.effect))
                        add(p.sensitivity(channel, saved.sensitivity.coerceIn(1, if (p.profile == ControllerProfile.COMPACT) 15 else 16)))
                    }
                    LightMode.CUSTOM -> Unit
                }
            }
            packets to c.light.copy(
                power = true, brightness = saved.brightness, color = saved.color, mode = saved.mode, effect = saved.effect,
                speed = saved.speed, sensitivity = saved.sensitivity, white = saved.white, cold = saved.cold, warm = saved.warm
            )
        }
    }

    fun addTimer(minutes: Int, turnOn: Boolean) {
        val current = _controls.value ?: return
        if (_timers.value.size >= MAX_POWER_TIMERS || minutes !in 1..720) return
        val duration = minutes * 60_000L
        val timer = ActiveTimer(nextTimerId++, SystemClock.elapsedRealtime() + duration, turnOn, current.channel)
        _timers.update { (it + timer).sortedBy(ActiveTimer::deadline) }
        timerJobs[timer.id] = viewModelScope.launch {
            delay(duration)
            val active = _controls.value?.takeIf { it.device == current.device && timer.channel in it.outputs.indices }
            if (active != null) {
                if (!turnOn && timer.channel == active.channel) {
                    stopSequence()
                    stopAudioSync()
                }
                val packet = ControllerProtocol(active.device.profile).power(timer.channel, turnOn)
                if (send(active, listOf(packet))) {
                    updateOutput(active, timer.channel, active.outputs[timer.channel].copy(power = turnOn))
                }
            }
            timerJobs.remove(timer.id)
            _timers.update { timers -> timers.filterNot { it.id == timer.id } }
        }
    }

    fun cancelTimer(id: Long) {
        timerJobs.remove(id)?.cancel()
        _timers.update { timers -> timers.filterNot { it.id == id } }
    }

    private fun cancelTimers() {
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        _timers.value = emptyList()
    }

    fun startPhoneMic() = startAudioSync(AudioSyncSource.MICROPHONE, AudioReactiveEngine::captureMicrophone)

    @RequiresApi(Build.VERSION_CODES.Q)
    fun startNowPlaying(consent: Intent) = startAudioSync(AudioSyncSource.PLAYBACK) { onLevel ->
        PlaybackCaptureService.capture(getApplication(), consent, onLevel)
    }

    private fun startAudioSync(source: AudioSyncSource, capture: suspend (suspend (Float) -> Unit) -> Unit) {
        val current = _controls.value ?: return
        if (audioJob?.isActive == true || !current.device.hasRgb(current.light)) return
        _audioSync.value = AudioSync(source)
        audioJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                capture { level ->
                    withContext(Dispatchers.Main) {
                        val sync = _audioSync.value ?: return@withContext
                        if (audioJob !== this@launch.coroutineContext[Job]) return@withContext
                        val active = _controls.value?.takeIf { it.device == current.device && it.channel == current.channel }
                            ?: return@withContext
                        if (sync.level == null) {
                            setColor(active.light.color)
                            if (!active.light.power) power(true)
                        }
                        _audioSync.value = sync.copy(level = level)
                        sendAudioBrightness(active, audioBrightness(active.light.brightness, level))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.value = when (source) {
                    AudioSyncSource.MICROPHONE -> "Microphone sync stopped. Check microphone access and try again."
                    AudioSyncSource.PLAYBACK -> "Now Playing sync stopped. Allow audio capture and try again."
                }
            } finally {
                if (audioJob === currentCoroutineContext()[Job]) {
                    audioJob = null
                    clearAudioSync(restore = true)
                }
            }
        }
        audioJob?.start()
    }

    private fun stopAudioSync(restore: Boolean) {
        val job = audioJob
        audioJob = null
        job?.cancel()
        clearAudioSync(restore)
    }

    private fun clearAudioSync(restore: Boolean) {
        val wasPulsing = _audioSync.value?.level != null
        _audioSync.value = null
        if (wasPulsing && restore) _controls.value?.let { sendAudioBrightness(it, it.light.brightness) }
    }

    private fun sendAudioBrightness(current: DeviceControls, level: Int) {
        val packet = ControllerProtocol(current.device.profile).brightness(current.channel, level)
        send(current, listOf(packet), "audio-sync-${current.channel}")
    }

    fun stopAudioSync() = stopAudioSync(restore = true)

    fun saveSequence(id: String?, name: String, steps: List<ColorStep>, loop: Boolean) {
        val sequence = ColorSequence(id ?: UUID.randomUUID().toString(), name.trim(), steps.toList(), loop)
        if (_playingSequence.value == id) stopSequence()
        val previous = _sequences.value
        updateSequences(if (id == null) previous + sequence else previous.map { if (it.id == id) sequence else it })
    }

    fun deleteSequence(id: String) {
        if (_playingSequence.value == id) stopSequence()
        updateSequences(_sequences.value.filterNot { it.id == id })
    }

    fun playSequence(sequence: ColorSequence) {
        val selected = _controls.value ?: return
        if (!selected.device.hasRgb(selected.light)) return
        stopSequence()
        stopAudioSync()
        val protocol = ControllerProtocol(selected.device.profile)
        _playingSequence.value = sequence.id
        sequenceJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                do {
                    for (step in sequence.steps) {
                        val current = _controls.value ?: return@launch
                        if (current.device != selected.device || current.channel != selected.channel) return@launch
                        val light = current.light
                        val packets = buildList {
                            if (!light.power) add(protocol.power(current.channel, true))
                            addAll(protocol.color(current.channel, step.color, light.brightness, light.mode != LightMode.COLOR))
                        }
                        if (!send(current, packets, "sequence-${current.channel}".takeIf { light.power && light.mode == LightMode.COLOR })) {
                            return@launch
                        }
                        updateOutput(current, current.channel, light.copy(
                            power = true, color = step.color, mode = LightMode.COLOR, effect = protocol.profile.solidEffect
                        ))
                        delay(step.seconds * 1_000L)
                    }
                } while (sequence.loop)
            } finally {
                if (sequenceJob === currentCoroutineContext()[Job]) _playingSequence.value = null
            }
        }
        sequenceJob?.start()
    }

    fun stopSequence() {
        _playingSequence.value = null
        sequenceJob?.cancel()
        sequenceJob = null
    }

    private fun updateSequences(sequences: List<ColorSequence>) {
        _sequences.value = sequences
        if (_controls.value?.demoName == null) library.storeSequences(sequences)
    }

    fun showMessage(message: String) { _message.value = message }
    fun clearMessage(displayed: String) { _message.compareAndSet(displayed, null) }

    private fun change(key: String? = null, build: (ControllerProtocol, DeviceControls) -> Pair<List<ByteArray>, LightState>) {
        val current = _controls.value ?: return
        stopSequence()
        val (packets, next) = build(ControllerProtocol(current.device.profile), current)
        val coalesce = key?.let { "$it-${current.channel}" }?.takeIf { current.light.mode == next.mode }
        if (send(current, packets, coalesce)) updateOutput(current, current.channel, next)
    }

    private fun send(current: DeviceControls, packets: List<ByteArray>, key: String? = null): Boolean =
        if (current.demoName != null) packets.isNotEmpty() else ble.send(packets, key)

    private fun updateOutput(current: DeviceControls, channel: Int, light: LightState) {
        _controls.update { active ->
            active?.takeIf { it.device == current.device }?.let {
                it.copy(outputs = it.outputs.toMutableList().apply { this[channel] = light })
            }
        }
    }

    private fun updateControllers(devices: List<SavedController>) {
        _controllers.value = devices
        library.storeControllers(devices)
    }

    private fun updateLooks(looks: List<SavedLook>) {
        _looks.value = looks
        if (_controls.value?.demoName == null) library.storeLooks(looks)
    }

    override fun onCleared() {
        ble.close()
        super.onCleared()
    }
}
