// SPDX-License-Identifier: AGPL-3.0-only

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.emitloom.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.LightMode
import com.emitloom.app.model.SavedLook
import com.emitloom.app.model.effects
import com.emitloom.app.model.hasControllerMic
import com.emitloom.app.model.hasRgb
import com.emitloom.app.model.hasWhite
import com.emitloom.app.model.lightType
import com.emitloom.app.protocol.ControllerProfile
import com.emitloom.app.ui.components.ColorWheel
import com.emitloom.app.ui.components.StripPreview
import com.emitloom.app.ui.viewmodel.ActiveTimer
import com.emitloom.app.ui.viewmodel.DeviceControls
import com.emitloom.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class ControlPage(val label: String, val icon: ImageVector) {
    COLOR("Color", Icons.Rounded.Palette), EFFECTS("Effects", Icons.Rounded.PlayArrow),
    SOUND("Sound", Icons.Rounded.GraphicEq), LOOKS("Looks", Icons.Rounded.StarBorder)
}

private val quickColors = listOf(
    "Red" to ColorRgb(255, 77, 73), "Amber" to ColorRgb(255, 170, 53),
    "Yellow" to ColorRgb(245, 232, 75), "Green" to ColorRgb(53, 230, 138),
    "Blue" to ColorRgb(64, 189, 255), "Violet" to ColorRgb(133, 103, 255),
    "White" to ColorRgb(255, 255, 255)
)

@Composable
internal fun ControllerScreen(model: MainViewModel, current: DeviceControls, onSettings: () -> Unit, onPhoneMic: () -> Unit) {
    val light = current.light
    val device = current.device
    val scene = device.profile == ControllerProfile.FRAMED
    val rgb = device.hasRgb(light)
    val white = device.hasWhite(light)
    val looks by model.looks.collectAsStateWithLifecycle()
    val timer by model.timer.collectAsStateWithLifecycle()
    val mic by model.phoneMic.collectAsStateWithLifecycle()
    var page by rememberSaveable(device.address, current.channel) {
        mutableStateOf(when (light.mode) {
            LightMode.EFFECTS, LightMode.WHITE_EFFECTS -> ControlPage.EFFECTS
            LightMode.MUSIC, LightMode.WHITE_MUSIC -> ControlPage.SOUND
            else -> ControlPage.COLOR
        })
    }
    var whiteColor by rememberSaveable(device.address, current.channel) { mutableStateOf(!rgb || light.mode == LightMode.WHITE) }
    var whiteEffects by rememberSaveable(device.address, current.channel) {
        mutableStateOf(!rgb || light.mode in setOf(LightMode.WHITE_EFFECTS, LightMode.WHITE_MUSIC))
    }
    var query by rememberSaveable(device.address, page) { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf("") }
    var deleteLook by remember { mutableStateOf<SavedLook?>(null) }

    val effectMode = if (page == ControlPage.SOUND) {
        if (whiteEffects && scene) LightMode.WHITE_MUSIC else LightMode.MUSIC
    } else if (whiteEffects && scene) LightMode.WHITE_EFFECTS else LightMode.EFFECTS
    val showDeviceMic = device.hasControllerMic()
    val effectItems = remember(device, light.lightType, effectMode) { device.effects(light, effectMode) }
    val filteredEffects = remember(effectItems, query) { effectItems.filter { it.name.contains(query, ignoreCase = true) } }
    val stripEffects = remember(device, light.lightType, light.mode) { device.effects(light, light.mode) }
    val isEffectPage = page == ControlPage.EFFECTS || page == ControlPage.SOUND && showDeviceMic
    val activeEffect = light.mode == effectMode
    val spi = device.lightType(light)?.spi == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        stickyHeader {
            Box(Modifier.fillMaxWidth().background(Ink).padding(bottom = 4.dp)) {
                StripPreview(light, stripEffects, mic)
            }
        }
        item {
            PowerPanel(current, timer, model::power, onSettings) { dialog = "timer" }
        }
        if (current.outputs.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.outputs.indices.forEach { channel ->
                        FilterChip(
                            selected = channel == current.channel,
                            onClick = { model.selectChannel(channel) },
                            label = { Text("Output ${channel + 1}") }
                        )
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlPage.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == page,
                        onClick = { page = entry },
                        label = { Text(entry.label) },
                        leadingIcon = { Icon(entry.icon, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }

        if (page == ControlPage.COLOR) {
            if (rgb && white) {
                item {
                    ChoicePair("RGB", "White", whiteColor) { whiteColor = it }
                }
            }
            if (white && (whiteColor || !rgb)) {
                item {
                    ControlPanel {
                        LevelSlider("White brightness", light.white, 0..255, model::white, percent = true)
                        if (device.lightType(light)?.cct == true) {
                            LevelSlider("Warmth", light.warm, 0..255, model::temperature, percent = true)
                        }
                    }
                }
            } else if (rgb) {
                item {
                    ControlPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                SectionTitle("Solid color")
                                Text(light.color.toHex(), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                            }
                            TextButton(onClick = { dialog = "hex" }) { Text("Enter hex") }
                        }
                        LevelSlider("Brightness", light.brightness, 0..255, model::solidBrightness, percent = true)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ColorWheel(light.color, model::setColor, Modifier.widthIn(max = 244.dp).fillMaxWidth())
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            quickColors.forEach { (name, color) ->
                                ColorSwatch(name, color, light.color == color) { model.setColor(color) }
                            }
                        }
                        if (light.mode != LightMode.COLOR) {
                            FilledTonalButton(onClick = { model.setColor(light.color) }) { Text("Apply solid color") }
                        }
                    }
                }
            }
        }

        if (page == ControlPage.SOUND && rgb) {
            item {
                ControlPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Mic, null, tint = Mint)
                        Column(Modifier.weight(1f)) {
                            SectionTitle("Phone microphone")
                            Text("Pulse brightness with sound around your phone.", color = TextMuted)
                        }
                    }
                    if (mic != null) {
                        LinearProgressIndicator(progress = { mic ?: 0f }, modifier = Modifier.fillMaxWidth())
                        FilledTonalButton(onClick = model::stopPhoneMic) { Text("Stop sync") }
                    } else Button(onClick = onPhoneMic) { Text("Start sync") }
                }
            }
        }

        if (isEffectPage) {
            if (scene && rgb && white) item { ChoicePair("Color effects", "White effects", whiteEffects) { whiteEffects = it } }
            if (page == ControlPage.SOUND) item {
                SectionTitle("Controller microphone")
                if (current.demoName != null) Text("Sample sound patterns", color = TextMuted)
            }
            if (activeEffect) {
                item {
                    ControlPanel {
                        if (page == ControlPage.SOUND) {
                            val maxGain = if (device.profile == ControllerProfile.COMPACT) 15 else 16
                            LevelSlider("Sensitivity", light.sensitivity, 1..maxGain, model::sensitivity)
                            if (scene && light.input != 0) {
                                FilledTonalButton(onClick = { model.audioInput(0) }) { Text("Use built-in microphone") }
                            }
                        } else {
                            val isWhite = effectMode == LightMode.WHITE_EFFECTS
                            LevelSlider(
                                "Brightness", if (isWhite) light.white else light.brightness,
                                0..255, { model.brightness(it, isWhite) }, percent = true
                            )
                            LevelSlider("Speed", light.speed, 1..10, model::speed)
                            if (device.profile in setOf(ControllerProfile.A0, ControllerProfile.DUAL) ||
                                (scene && spi && light.length in 1..150)
                            ) LevelSlider("Effect length", light.length, 1..150, model::length)
                            if ((scene && spi && light.length > 0) || device.profile == ControllerProfile.DUAL) {
                                ToggleRow("Play forward", light.forward, model::direction)
                            }
                            if (scene && spi) ToggleRow("Play animation", light.playing, model::play)
                        }
                        if (device.profile != ControllerProfile.DUAL) ToggleRow("Cycle effects", light.loop, model::loop)
                    }
                }
            }
            if (effectItems.size >= 12) {
                item {
                    OutlinedTextField(
                        query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Find an effect") },
                        singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                        }
                    )
                }
            }
            if (filteredEffects.isEmpty()) item { Text("No matching effects.", color = TextMuted) }
            items(filteredEffects, key = { "effect-${effectMode.name}-${it.id}" }) { effect ->
                val selected = activeEffect && light.effect == effect.id
                EffectRow(effect.name, selected) { model.effect(effectMode, effect.id) }
            }
        }

        if (page == ControlPage.LOOKS) {
            if (rgb) item { SequencePanel(model, light.color) }
            val deviceLooks = looks.filter { it.address == device.address }
            item {
                ControlPanel {
                    SectionTitle("Saved looks")
                    Text("Save the active color or effect for this controller.", color = TextMuted)
                    Button(onClick = { dialog = "look" }, enabled = light.mode != LightMode.CUSTOM) {
                        Icon(Icons.Rounded.StarBorder, null, Modifier.size(18.dp))
                        Text("Save current look", Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (deviceLooks.isEmpty()) item { Text("No looks saved yet.", color = TextMuted) }
            items(deviceLooks, key = { it.id }) { look ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { model.applyLook(look) }) {
                    ListItem(
                        headlineContent = { Text(look.name) },
                        supportingContent = {
                            Text(if (current.outputs.size == 1) look.state.mode.label else "${look.state.mode.label} · Output ${look.channel + 1}")
                        },
                        leadingContent = {
                            Box(Modifier.size(24.dp).background(look.state.color.asColor(), CircleShape))
                        },
                        trailingContent = {
                            IconButton(onClick = { deleteLook = look }) { Icon(Icons.Rounded.DeleteOutline, "Delete ${look.name}") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            if (device.profile == ControllerProfile.DUAL) {
                item {
                    ControlPanel {
                        SectionTitle("Controller scene slots")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..9).forEach { slot -> FilledTonalButton(onClick = { model.recallSlot(slot) }) { Text("Slot $slot") } }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "hex" -> HexDialog(light.color, { dialog = "" }, model::setColor)
        "look" -> NameDialog("Save look", onDismiss = { dialog = "" }, onSave = model::saveLook)
        "timer" -> TimerDialog(timer, { dialog = "" }, model::setTimer, model::cancelTimer)
    }

    deleteLook?.let { look ->
        AlertDialog(
            onDismissRequest = { deleteLook = null },
            title = { Text("Delete saved look?") },
            text = { Text(look.name) },
            confirmButton = { TextButton(onClick = { model.deleteLook(look.id); deleteLook = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteLook = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PowerPanel(
    current: DeviceControls,
    timer: ActiveTimer?,
    onPower: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onTimer: () -> Unit
) {
    val light = current.light
    ControlPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { onPower(!light.power) },
                modifier = Modifier.size(54.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (light.power) Mint else PanelRaised,
                    contentColor = if (light.power) Color(0xFF0A2419) else TextPrimary
                )
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, if (light.power) "Turn light off" else "Turn light on")
            }
            Column(Modifier.weight(1f)) {
                Text(if (light.power) "Light on" else "Light off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (current.demoName != null) "Simulated ${current.device.model}" else "${current.device.model} · Connected",
                    color = TextMuted, style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Controller settings") }
        }

        HorizontalDivider(color = Divider)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = Mint, modifier = Modifier.size(20.dp))
            Text(
                timer?.let { "${if (it.turnOn) "On" else "Off"} in ${remainingTime(it)}" } ?: "Power timer",
                Modifier.weight(1f).padding(start = 10.dp)
            )
            TextButton(onClick = onTimer) { Text(if (timer == null) "Set timer" else "Manage") }
        }
    }
}

@Composable
internal fun ControlPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked, onCheckedChange = onChecked,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
private fun ChoicePair(first: String, second: String, selectedSecond: Boolean, onChoice: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !selectedSecond, onClick = { onChoice(false) }, label = { Text(first) })
        FilterChip(selected = selectedSecond, onClick = { onChoice(true) }, label = { Text(second) })
    }
}

@Composable
private fun LevelSlider(label: String, level: Int, range: IntRange, onLevel: (Int) -> Unit, percent: Boolean = false) {
    val value = level.coerceIn(range)
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(if (percent) "${(value * 100f / 255).roundToInt()}%" else value.toString(), color = Mint)
        }
        Slider(
            value.toFloat(), { onLevel(it.roundToInt()) },
            modifier = Modifier.semantics { contentDescription = label },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun EffectRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) PanelRaised else Panel,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.dp, Mint.copy(alpha = .65f)) else null
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, "Active effect", tint = Mint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: ColorRgb, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = name
                selected = isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) Box(Modifier.size(40.dp).border(2.dp, TextPrimary, CircleShape))
        Box(Modifier.size(30.dp).background(color.asColor(), CircleShape))
    }
}

@Composable
private fun HexDialog(color: ColorRgb, onDismiss: () -> Unit, onColor: (ColorRgb) -> Unit) {
    var input by rememberSaveable { mutableStateOf(color.toHex()) }
    val valid = ColorRgb.fromHex(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hex color") },
        text = {
            OutlinedTextField(input, { if (it.length <= 7) input = it }, label = { Text("RRGGBB") }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = valid != null, onClick = { valid?.let(onColor); onDismiss() }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TimerDialog(timer: ActiveTimer?, onDismiss: () -> Unit, onSet: (Int, Boolean) -> Unit, onCancel: () -> Unit) {
    var minutes by rememberSaveable { mutableStateOf("30") }
    var turnOn by rememberSaveable { mutableStateOf(false) }
    val duration = minutes.toIntOrNull()?.takeIf { it in 1..720 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Power timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The timer runs on this phone while EmitLoom stays connected.")
                if (timer != null) Text("${if (timer.turnOn) "On" else "Off"} in ${remainingTime(timer)}")
                ChoicePair("Turn off", "Turn on", turnOn) { turnOn = it }
                OutlinedTextField(
                    minutes, { if (it.length <= 3 && it.all(Char::isDigit)) minutes = it },
                    label = { Text("Minutes (1–720)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = duration != null, onClick = { duration?.let { onSet(it, turnOn) }; onDismiss() }) {
                Text(if (timer == null) "Start" else "Replace")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (timer != null) onCancel(); onDismiss() }) {
                Text(if (timer == null) "Cancel" else "Cancel timer")
            }
        }
    )
}

@Composable
private fun remainingTime(timer: ActiveTimer): String {
    fun secondsLeft() = ((timer.deadline - android.os.SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
    var seconds by remember(timer) { mutableLongStateOf(secondsLeft()) }
    LaunchedEffect(timer) {
        while (seconds > 0) {
            delay(1_000)
            seconds = secondsLeft()
        }
    }
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun ColorRgb.asColor() = Color(red, green, blue)
