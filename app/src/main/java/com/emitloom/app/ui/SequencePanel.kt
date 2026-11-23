// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.ColorSequence
import com.emitloom.app.model.ColorStep
import com.emitloom.app.ui.components.ColorWheel
import com.emitloom.app.ui.viewmodel.MainViewModel

private val SequenceFieldsSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it.toList() }
)

@Composable
internal fun SequencePanel(model: MainViewModel, color: ColorRgb) {
    val sequences by model.sequences.collectAsStateWithLifecycle()
    val playing by model.playingSequence.collectAsStateWithLifecycle()
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }

    ControlPanel {
        SectionTitle("Color sequences")
        Text("Play a series of colors on the selected output while EmitLoom is open.", color = TextMuted)
        Button(onClick = { editor = "" }) { Text("New sequence") }
        for (sequence in sequences) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sequence.name, style = MaterialTheme.typography.titleSmall)
                    Text("${sequence.steps.size} colors · ${if (sequence.loop) "Loop" else "Play once"}", color = TextMuted)
                }
                IconButton(onClick = { editor = sequence.id }) { Icon(Icons.Rounded.Edit, "Edit ${sequence.name}") }
                IconButton(onClick = { removing = sequence.id }) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete ${sequence.name}")
                }
            }
            FilledTonalButton(onClick = {
                if (playing == sequence.id) model.stopSequence() else model.playSequence(sequence)
            }) { Text(if (playing == sequence.id) "Stop" else "Play") }
        }
    }
    editor?.let { id ->
        val previous = sequences.firstOrNull { it.id == id }
        SequenceEditor(previous, color, { editor = null }) { name, steps, loop ->
            model.saveSequence(previous?.id, name, steps, loop)
            editor = null
        }
    }
    removing?.let { id ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Delete sequence?") },
            text = { Text(sequences.firstOrNull { it.id == id }?.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { model.deleteSequence(id); removing = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SequenceEditor(
    existing: ColorSequence?,
    color: ColorRgb,
    dismiss: () -> Unit,
    save: (String, List<ColorStep>, Boolean) -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var loop by rememberSaveable(existing?.id) { mutableStateOf(existing?.loop ?: false) }
    var colors by rememberSaveable(existing?.id, stateSaver = SequenceFieldsSaver) {
        mutableStateOf(existing?.steps?.map { it.color.toHex() } ?: listOf(color.toHex(), "#FFFFFF"))
    }
    var holds by rememberSaveable(existing?.id, stateSaver = SequenceFieldsSaver) {
        mutableStateOf(existing?.steps?.map { it.seconds.toString() } ?: listOf("2", "2"))
    }
    var editingColor by rememberSaveable(existing?.id) { mutableStateOf<Int?>(null) }
    val editorScroll = rememberScrollState()
    editingColor?.let { index ->
        StepColorDialog(index, colors[index], { editingColor = null }) { hex ->
            colors = colors.toMutableList().apply { this[index] = hex }
            editingColor = null
        }
        return
    }
    val steps = colors.zip(holds) { hex, hold -> ColorStep.parse(hex, hold) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (existing == null) "New sequence" else "Edit sequence") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(editorScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text("Name") }, singleLine = true)
                Text("Pick a color and a hold time of 1–120 seconds for each step.")
                for (index in colors.indices) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rgb = ColorRgb.fromHex(colors[index])
                        OutlinedButton(onClick = { editingColor = index }, modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(14.dp).background(rgb?.asColor() ?: PanelRaised, CircleShape))
                                Text("Color ${index + 1}")
                            }
                        }
                        OutlinedTextField(
                            holds[index], { hold ->
                                if (hold.length <= 3 && hold.all(Char::isDigit)) {
                                    holds = holds.mapIndexed { i, value -> if (i == index) hold else value }
                                }
                            },
                            Modifier.weight(.65f), label = { Text("Sec") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = (holds[index].toIntOrNull() ?: 0) !in 1..120
                        )
                        if (colors.size > 2) {
                            IconButton(onClick = {
                                colors = colors.filterIndexed { i, _ -> i != index }
                                holds = holds.filterIndexed { i, _ -> i != index }
                            }) { Icon(Icons.Rounded.DeleteOutline, "Remove step ${index + 1}") }
                        }
                    }
                }
                TextButton(enabled = colors.size < 12, onClick = {
                    colors = colors + color.toHex()
                    holds = holds + "2"
                }) { Text("Add color") }
                ToggleRow("Loop sequence", loop) { loop = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && steps.none { it == null }, onClick = {
                save(name, steps.filterNotNull(), loop)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StepColorDialog(index: Int, initial: String, dismiss: () -> Unit, apply: (String) -> Unit) {
    var hex by rememberSaveable(index) { mutableStateOf(initial) }
    val rgb = ColorRgb.fromHex(hex)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Color ${index + 1}") },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ColorWheel(rgb ?: ColorRgb.fromHex(initial) ?: ColorRgb(255, 255, 255), { hex = it.toHex() },
                    Modifier.widthIn(max = 220.dp).fillMaxWidth())
                OutlinedTextField(hex, { if (it.length <= 7) hex = it }, label = { Text("Hex (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = rgb != null, onClick = { rgb?.let { apply(it.toHex()) } }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}
