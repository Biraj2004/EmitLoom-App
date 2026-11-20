// SPDX-License-Identifier: AGPL-3.0-only

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emitloom.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emitloom.app.bluetooth.DiscoveryState
import com.emitloom.app.model.SavedController
import com.emitloom.app.ui.viewmodel.MainViewModel

private enum class Page { DEVICES, CONTROL, SETTINGS }

@Composable
fun EmitLoomApp(model: MainViewModel, onScan: () -> Unit, onConnect: (SavedController) -> Unit, onPhoneMic: () -> Unit) {
    val saved by model.controllers.collectAsStateWithLifecycle()
    val discovery by model.discovery.collectAsStateWithLifecycle()
    val connection by model.connection.collectAsStateWithLifecycle()
    val controls by model.controls.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(Page.DEVICES) }
    var scanner by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controls?.device) {
        if (controls != null) page = Page.CONTROL
        else page = Page.DEVICES
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.clearMessage(it)
        }
    }

    fun goBack() {
        page = if (page == Page.SETTINGS) Page.CONTROL else Page.DEVICES
    }

    BackHandler(page != Page.DEVICES && !scanner) { goBack() }
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (page) {
                            Page.DEVICES -> "EmitLoom"
                            Page.CONTROL -> saved.firstOrNull { it.device == controls?.device }?.name ?: "Controller"
                            Page.SETTINGS -> "Controller settings"
                        },
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (page != Page.DEVICES) {
                        IconButton(onClick = ::goBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (page == Page.DEVICES) {
                        IconButton(onClick = { about = true }) { Icon(Icons.Rounded.Info, "About EmitLoom") }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                val active = controls
                when {
                    active != null && page == Page.SETTINGS -> ControllerSettings(
                        model, active, saved.firstOrNull { it.device == active.device }
                    )
                    active != null && page == Page.CONTROL -> ControllerScreen(
                        model, active, onSettings = { page = Page.SETTINGS }, onPhoneMic = onPhoneMic
                    )
                    else -> DeviceList(
                        saved = saved,
                        connected = active?.device?.address,
                        connecting = connection.device?.address.takeIf { connection.connecting },
                        error = connection.error,
                        onCancelConnection = model::disconnect,
                        onFind = { scanner = true; onScan() },
                        onOpen = {
                            if (active?.device?.address == it.device.address) page = Page.CONTROL else onConnect(it)
                        }
                    )
                }
            }
        }
    }

    if (scanner) {
        DiscoverySheet(
            discovery, message, onScan,
            onDismiss = { scanner = false; model.stopScan() },
            onConnect = { found ->
                val controller = saved.firstOrNull { it.device.address == found.device.address }
                    ?: SavedController(found.device, found.name)
                scanner = false
                model.stopScan()
                onConnect(controller)
            }
        )
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("EmitLoom") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Version 1.0.0")
                    Text("Control supported LED lights over Bluetooth. Your saved controllers and looks stay on this phone.")
                    Text("Licensed under the GNU Affero General Public License v3.0.")
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun DeviceList(
    saved: List<SavedController>,
    connected: String?,
    connecting: String?,
    error: String?,
    onCancelConnection: () -> Unit,
    onFind: () -> Unit,
    onOpen: (SavedController) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LedStripIllustration(Modifier.fillMaxWidth().height(86.dp))
                Text("Your lights", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text("Find nearby Bluetooth LED controllers and manage them from one place.", color = TextMuted)
                Button(onClick = onFind, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                    Text("Find lights", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (connecting != null) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reading controller status…", Modifier.weight(1f), color = TextMuted)
                    TextButton(onClick = onCancelConnection) { Text("Cancel") }
                }
            }
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
        }
        item { SectionTitle("Controllers") }
        if (saved.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("No controllers saved") },
                    supportingContent = { Text("Turn on your controller, then choose Find lights.") },
                    leadingContent = { Icon(Icons.Rounded.Bluetooth, null, tint = Mint) },
                    colors = ListItemDefaults.colors(containerColor = Panel),
                    modifier = Modifier.background(Panel, RoundedCornerShape(14.dp))
                )
            }
        }
        items(saved, key = { it.device.address }) { item ->
            val address = item.device.address
            val description = when (address) {
                connected -> "${item.device.model} · Connected"
                connecting -> "${item.device.model} · Connecting"
                else -> item.device.model
            }
            ControllerRow(item.name, description, onClick = { onOpen(item) })
        }
    }
}

@Composable
private fun LedStripIllustration(modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val rail = h * .42f
        val origin = Offset(0f, (h - rail) * .5f)
        drawRoundRect(PanelRaised, origin, Size(w, rail), CornerRadius(rail / 4))
        val count = 10
        val pitch = w / count
        val colors = listOf(Mint, Color(0xFF8FDAEE), Color(0xFFF5D6A3))
        for (index in 0 until count) {
            val center = Offset(pitch * (index + .5f), h * .5f)
            val shade = colors[index * colors.size / count]
            drawCircle(shade.copy(alpha = .16f), rail * .31f, center)
            drawCircle(shade, rail * .17f, center)
        }
    }
}

@Composable
private fun DiscoverySheet(
    state: DiscoveryState,
    message: String?,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
    onConnect: (com.emitloom.app.bluetooth.ScanEntry) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nearby lights", style = MaterialTheme.typography.headlineSmall)
                        Text(if (state.scanning) "Scanning for controllers…" else "Select a controller to connect.", color = TextMuted)
                    }
                    if (state.scanning) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = onScan) { Icon(Icons.Rounded.Refresh, "Scan again") }
                }
            }
            val error = state.error ?: message
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.devices.isEmpty() && !state.scanning && error == null) {
                item { Text("No supported lights found yet. Keep your controller powered on and close to your phone.", color = TextMuted) }
            }
            items(state.devices, key = { it.device.address }) { item ->
                ControllerRow(item.name, "${item.device.model} · ${item.signal} dBm", raised = true) { onConnect(item) }
            }
            item {
                if (!state.scanning && state.devices.isEmpty()) {
                    FilledTonalButton(onClick = onScan) { Text("Scan again") }
                } else Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ControllerRow(name: String, detail: String, raised: Boolean = false, onClick: () -> Unit) {
    val background = if (raised) PanelRaised else Panel
    Box(Modifier.fillMaxWidth().background(background, RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = { Text(detail) },
            leadingContent = { Icon(Icons.Rounded.Lightbulb, null, tint = Mint) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()); onDismiss() }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
