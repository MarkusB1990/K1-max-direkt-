package de.k1direkt.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.FileType
import de.k1direkt.data.LibraryFile
import de.k1direkt.slicer.Material
import de.k1direkt.slicer.SliceParams
import de.k1direkt.slicer.SupportMode
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.SliceUiState
import de.k1direkt.ui.Tab

private val LAYER_HEIGHTS = listOf(0.08f, 0.12f, 0.16f, 0.20f, 0.24f, 0.28f)
private val SPEEDS = listOf(60 to "Leise", 100 to "Standard", 130 to "Schnell")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliceScreen(vm: AppViewModel, nav: Navigator, item: LibraryFile) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sliceState by vm.slice.collectAsStateWithLifecycle()
    val files by vm.library.files.collectAsStateWithLifecycle()
    var params by remember { mutableStateOf(settings.defaultSlice) }
    var saveDefault by remember { mutableStateOf(false) }
    val meshState = if (item.type != FileType.STEP) rememberMesh(item.file) else MeshState.Failed("Keine Vorschau für STEP-Dateien")

    DisposableEffect(Unit) { onDispose { vm.resetSlice() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            title = {
                Column {
                    Text("Slicen für K1 Max")
                    Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Card(shape = RoundedCornerShape(12.dp)) {
                ModelPreview(meshState, Modifier.fillMaxWidth().height(220.dp))
            }
            (meshState as? MeshState.Ready)?.mesh?.let { mesh ->
                val sx = mesh.sizeX * params.scalePercent / 100f
                val sy = mesh.sizeY * params.scalePercent / 100f
                val sz = mesh.sizeZ * params.scalePercent / 100f
                val fits = sx <= 300 && sy <= 300 && sz <= 300
                Text(
                    "Größe: %.1f × %.1f × %.1f mm".format(sx, sy, sz) + if (!fits) "  – passt nicht auf die Platte!" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            val done = sliceState as? SliceUiState.Done
            if (done != null) {
                val st = done
                    val gcode = files.firstOrNull { it.file == st.result.gcode }
                    SliceDone(
                        printTime = st.result.printTime,
                        filament = listOfNotNull(
                            st.result.filamentMeters?.let { "%.2f m".format(it) },
                            st.result.filamentGrams?.let { "%.0f g".format(it) },
                        ).joinToString(" · ").ifBlank { null },
                        fileName = st.result.gcode.name,
                        onPrint = { gcode?.let { vm.sendToPrinter(it, start = true); nav.openTab(Tab.PRINTER) } },
                        onUpload = { gcode?.let { vm.sendToPrinter(it, start = false) } },
                        onLibrary = { nav.openTab(Tab.LIBRARY) },
                        onAgain = { vm.resetSlice() },
                    )
            } else {
            SectionTitle("Material")
            ChipRow(Material.entries, params.material, { it.label }) {
                params = params.copy(material = it, nozzleTemp = 0, bedTemp = 0)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField("Düse °C", params.effectiveNozzleTemp, Modifier.weight(1f)) { params = params.copy(nozzleTemp = it) }
                NumberField("Bett °C", params.effectiveBedTemp, Modifier.weight(1f)) { params = params.copy(bedTemp = it) }
            }

            SectionTitle("Schichthöhe: %.2f mm".format(params.layerHeight))
            ChipRow(LAYER_HEIGHTS, params.layerHeight, { "%.2f".format(it) }) { params = params.copy(layerHeight = it) }

            SectionTitle("Füllung: ${params.infillPercent} %")
            Slider(
                value = params.infillPercent.toFloat(),
                onValueChange = { params = params.copy(infillPercent = (it / 5).toInt() * 5) },
                valueRange = 0f..100f,
                steps = 19,
            )

            SectionTitle("Stützstrukturen")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SupportMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = params.support == mode,
                        onClick = { params = params.copy(support = mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, SupportMode.entries.size),
                    ) { Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            SectionTitle("Geschwindigkeit")
            ChipRow(SPEEDS.map { it.first }, params.speedPercent, { v -> SPEEDS.first { it.first == v }.second }) {
                params = params.copy(speedPercent = it)
            }

            SectionTitle("Weitere Einstellungen")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Brim (Haftrand 5 mm)", Modifier.weight(1f))
                Switch(checked = params.brimWidth > 0f, onCheckedChange = { params = params.copy(brimWidth = if (it) 5f else 0f) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField("Wände", params.perimeters, Modifier.weight(1f)) { params = params.copy(perimeters = it.coerceIn(1, 10)) }
                NumberField("Skalierung %", params.scalePercent, Modifier.weight(1f)) { params = params.copy(scalePercent = it.coerceIn(1, 1000)) }
                NumberField("Drehung °", params.rotateZ, Modifier.weight(1f)) { params = params.copy(rotateZ = it % 360) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(checked = saveDefault, onCheckedChange = { saveDefault = it })
                Text("Als Standard merken")
            }

            if (sliceState is SliceUiState.Failed) {
                Spacer(Modifier.height(8.dp))
                InfoCard(
                    title = "Slicen fehlgeschlagen",
                    text = (sliceState as SliceUiState.Failed).message,
                    icon = Icons.Filled.Error,
                )
            }
            if (!settings.slicerConfigured) {
                Spacer(Modifier.height(8.dp))
                InfoCard(
                    title = "Slicer-Server fehlt",
                    text = "Android kann OrcaSlicer nicht selbst ausführen. Starte den mitgelieferten Slicer-Server " +
                        "(Home-Assistant-App oder Docker, Ordner slicer-server) und trage die Adresse in den Einstellungen ein.",
                    action = { TextButton(onClick = { nav.openTab(Tab.SETTINGS) }) { Text("Zu den Einstellungen") } },
                )
            }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (sliceState !is SliceUiState.Done) {
            val running = sliceState is SliceUiState.Running
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("Wird gesliced …", Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.resetSlice() }) { Text("Abbrechen") }
                } else {
                    Button(
                        onClick = { vm.startSlice(item, params, saveDefault) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Layers, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Jetzt slicen")
                    }
                }
            }
        }
    }
}

@Composable
private fun SliceDone(
    printTime: String?,
    filament: String?,
    fileName: String,
    onPrint: () -> Unit,
    onUpload: () -> Unit,
    onLibrary: () -> Unit,
    onAgain: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Fertig gesliced!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(fileName, style = MaterialTheme.typography.bodySmall)
            printTime?.let { Text("Druckzeit: $it") }
            filament?.let { Text("Filament: $it") }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPrint, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("An K1 Max senden & drucken")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text("Nur auf den Drucker laden")
            }
            Row {
                TextButton(onClick = onLibrary) { Text("Zur Bibliothek") }
                TextButton(onClick = onAgain) { Text("Neu slicen") }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollCompat(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == '-' }.take(5)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
