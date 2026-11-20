// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emitloom.app.model.LightType
import com.emitloom.app.model.SavedController
import com.emitloom.app.model.chipOrders
import com.emitloom.app.model.lightType
import com.emitloom.app.protocol.ControllerProfile
import com.emitloom.app.ui.viewmodel.DeviceControls
import com.emitloom.app.ui.viewmodel.MainViewModel

@Composable
internal fun ControllerSettings(model: MainViewModel, current: DeviceControls, controller: SavedController?) {
    val device = current.device
    val light = current.light
    val type = device.lightType(light)
    val scene = device.profile == ControllerProfile.FRAMED
    val sequence = when {
        scene -> type?.order.orEmpty()
        device.profile == ControllerProfile.COMPACT -> ""
        device.model == "SP617E" -> "RGBW"
        else -> "RGB"
    }
    val orders = chipOrders(sequence)
    val powerModes = listOf("Stay off", "Turn on", "Restore last state")
    var dialog by rememberSaveable { mutableStateOf("") }
    var pendingType by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ControlPanel {
                SectionTitle(controller?.name ?: device.model)
                Text(device.model, color = TextMuted)
                if (light.firmware.isNotBlank()) Text("Firmware ${light.firmware}", color = TextMuted)
                SettingsRow("Display name", "Saved on this phone", onClick = { dialog = "rename" })
            }
        }
        if (type != null || orders.isNotEmpty() || scene) {
            item {
                ControlPanel {
                    SectionTitle("Output configuration")
                    if (device.model == "SP630E" && type != null) {
                        SettingsRow("Output type", type.label) { dialog = "type" }
                    } else if (type != null) {
                        Text(type.label, color = TextMuted)
                    }
                    if (orders.isNotEmpty()) {
                        SettingsRow("Channel order", orders.getOrNull(light.order) ?: "Unknown") { dialog = "order" }
                    }
                    if (type?.coexist == true) ToggleRow("Allow color and white together", light.coexist, model::coexist)
                    if (scene) {
                        SettingsRow("After power is restored", powerModes.getOrNull(light.powerRestore) ?: "Unknown") {
                            dialog = "restore"
                        }
                    }
                }
            }
        }
        item {
            ControlPanel {
                SectionTitle("Bluetooth")
                FilledTonalButton(onClick = model::refresh) {
                    Icon(Icons.Rounded.Refresh, null)
                    Text("Read controller status", Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = model::disconnect) {
                    Icon(Icons.Rounded.BluetoothDisabled, null)
                    Text("Disconnect", Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = { dialog = "forget" }) { Text("Forget controller") }
            }
        }
    }

    when (dialog) {
        "rename" -> NameDialog("Rename controller", controller?.name.orEmpty(), { dialog = "" }) {
            model.rename(device.address, it)
        }
        "order" -> OptionsDialog("Channel order", orders, light.order, { dialog = "" }, model::colorOrder)
        "restore" -> OptionsDialog("After power is restored", powerModes, light.powerRestore, { dialog = "" }, model::powerRestore)
        "type" -> OptionsDialog(
            "Output type", LightType.entries.map { it.label }, type?.ordinal ?: -1,
            { dialog = "" }, { pendingType = it.takeIf { index -> index != type?.ordinal } }
        )
        "forget" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("Forget this controller?") },
            text = { Text("Its name and saved looks will be removed from EmitLoom.") },
            confirmButton = { TextButton(onClick = { dialog = ""; model.forget(device.address) }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } }
        )
    }
    pendingType?.let { selection ->
        val next = LightType.entries[selection]
        AlertDialog(
            onDismissRequest = { pendingType = null },
            title = { Text("Change output type?") },
            text = { Text("Apply ${next.label}, switch off the light, and reset channel order. Select the type that matches the connected LEDs.") },
            confirmButton = { TextButton(onClick = { pendingType = null; model.lightType(next) }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { pendingType = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    HorizontalDivider(color = Divider)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun OptionsDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 335.dp)) {
                    itemsIndexed(options) { index, option ->
                        ListItem(
                            headlineContent = { Text(option) },
                            leadingContent = { RadioButton(selected == index, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onDismiss(); onSelect(index) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
