package de.k1direkt.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.FileType
import de.k1direkt.data.LibraryFile
import de.k1direkt.slicer.Material
import de.k1direkt.slicer.SliceParams
import de.k1direkt.slicer.SliceResult
import de.k1direkt.slicer.SupportMode
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.SliceUiState
import de.k1direkt.ui.Tab
import de.k1direkt.ui.theme.K1Blue
import de.k1direkt.ui.theme.K1Green
import java.util.Locale

private enum class Quality(val label: String, val layer: Float) {
    DRAFT("Schnell", 0.28f), STANDARD("Standard", 0.20f), FINE("Fein", 0.12f)
}

private val LAYER_HEIGHTS = listOf(0.08f, 0.12f, 0.16f, 0.20f, 0.24f, 0.28f)

@Composable
fun SliceScreen(vm: AppViewModel, nav: Navigator, item: LibraryFile) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sliceState by vm.slice.collectAsStateWithLifecycle()
    val files by vm.library.files.collectAsStateWithLifecycle()
    var params by remember { mutableStateOf(settings.defaultSlice) }
    var saveDefault by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val meshState = if (item.type != FileType.STEP) rememberMesh(item.file) else MeshState.Failed("Keine Vorschau für STEP-Dateien")

    DisposableEffect(Unit) { onDispose { vm.resetSlice() } }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(title = "Slicen", subtitle = item.name, onBack = { nav.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Box {
                    ModelPreview(meshState, Modifier.fillMaxWidth().height(210.dp))
                    (meshState as? MeshState.Ready)?.mesh?.let { mesh ->
                        val f = params.scalePercent / 100f
                        val fits = mesh.sizeX * f <= 300 && mesh.sizeY * f <= 300 && mesh.sizeZ * f <= 300
                        Pill(
                            String.format(Locale.GERMANY, "%.0f × %.0f × %.0f mm", mesh.sizeX * f, mesh.sizeY * f, mesh.sizeZ * f) +
                                if (!fits) " · zu groß!" else "",
                            if (fits) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.error,
                            onColor = if (fits) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onError,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        )
                    }
                }
            }

            val done = sliceState as? SliceUiState.Done
            if (done != null) {
                SliceDone(
                    result = done.result,
                    onPrint = { files.firstOrNull { it.file == done.result.gcode }?.let { vm.sendToPrinter(it, start = true); nav.openTab(Tab.PRINTER) } },
                    onUpload = { files.firstOrNull { it.file == done.result.gcode }?.let { vm.sendToPrinter(it, start = false) } },
                    onLibrary = { nav.openTab(Tab.LIBRARY) },
                    onAgain = { vm.resetSlice() },
                )
            } else {
                SectionCard(title = "Material") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Material.entries.forEach { m ->
                            FilterChip(
                                selected = params.material == m,
                                onClick = { params = params.copy(material = m, nozzleTemp = 0, bedTemp = 0) },
                                label = { Text(m.label) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            )
                        }
                    }
                    Text(
                        "${params.effectiveNozzleTemp} °C Düse · ${params.effectiveBedTemp} °C Bett",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                SectionCard(title = "Qualität") {
                    val q = Quality.entries.firstOrNull { it.layer == params.layerHeight }
                    Segmented(Quality.entries, q ?: Quality.STANDARD, { it.label }) { params = params.copy(layerHeight = it.layer) }
                    Text(
                        "Schichthöhe %.2f mm".format(Locale.GERMANY, params.layerHeight) + if (q == null) " (eigene)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                SectionCard(title = "Stützen") {
                    Segmented(SupportMode.entries, params.support, { if (it == SupportMode.NONE) "Aus" else it.label }) { params = params.copy(support = it) }
                }

                SectionCard(title = "Füllung · ${params.infillPercent} %") {
                    Slider(
                        value = params.infillPercent.toFloat(),
                        onValueChange = { params = params.copy(infillPercent = (it / 5).toInt() * 5) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }

                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Erweitert", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                        AnimatedVisibility(advanced) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NumberField("Düse °C", params.effectiveNozzleTemp, Modifier.weight(1f)) { params = params.copy(nozzleTemp = it) }
                                    NumberField("Bett °C", params.effectiveBedTemp, Modifier.weight(1f)) { params = params.copy(bedTemp = it) }
                                }
                                Text("Schichthöhe", style = MaterialTheme.typography.labelLarge)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LAYER_HEIGHTS.forEach { h ->
                                        FilterChip(
                                            selected = params.layerHeight == h,
                                            onClick = { params = params.copy(layerHeight = h) },
                                            label = { Text("%.2f".format(Locale.GERMANY, h)) },
                                            shape = RoundedCornerShape(50),
                                        )
                                    }
                                }
                                Text("Geschwindigkeit", style = MaterialTheme.typography.labelLarge)
                                Segmented(listOf(60, 100, 130), params.speedPercent, {
                                    when (it) { 60 -> "Leise"; 100 -> "Normal"; else -> "Schnell" }
                                }) { params = params.copy(speedPercent = it) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Brim (Haftrand)", Modifier.weight(1f))
                                    Switch(checked = params.brimWidth > 0f, onCheckedChange = { params = params.copy(brimWidth = if (it) 5f else 0f) })
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    NumberField("Wände", params.perimeters, Modifier.weight(1f)) { params = params.copy(perimeters = it.coerceIn(1, 10)) }
                                    NumberField("Größe %", params.scalePercent, Modifier.weight(1f)) { params = params.copy(scalePercent = it.coerceIn(1, 1000)) }
                                    NumberField("Drehen °", params.rotateZ, Modifier.weight(1f)) { params = params.copy(rotateZ = it % 360) }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = saveDefault, onCheckedChange = { saveDefault = it })
                                    Text("Als Standard merken")
                                }
                            }
                        }
                    }
                }

                (sliceState as? SliceUiState.Failed)?.let {
                    InfoCard("Slicen fehlgeschlagen", it.message, icon = Icons.Filled.Error, tint = MaterialTheme.colorScheme.error)
                }
                if (!settings.slicerConfigured) {
                    InfoCard(
                        title = "Slicer-Server fehlt",
                        text = "Trage in den Einstellungen die Adresse deines Slicer-Servers ein (z. B. die Home-Assistant-App „K1 Max Slicer“).",
                        action = { TextButton(onClick = { nav.openTab(Tab.SETTINGS) }) { Text("Zu den Einstellungen") } },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (sliceState !is SliceUiState.Done) {
            val running = sliceState is SliceUiState.Running
            Surface(color = MaterialTheme.colorScheme.background) {
                if (running) {
                    Row(Modifier.fillMaxWidth().padding(ScreenPadding).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("OrcaSlicer arbeitet …", style = MaterialTheme.typography.titleSmall)
                            Text("Das kann bei großen Modellen etwas dauern", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.resetSlice() }) { Text("Abbrechen") }
                    }
                } else {
                    Button(
                        onClick = { vm.startSlice(item, params, saveDefault) },
                        modifier = Modifier.fillMaxWidth().padding(ScreenPadding).height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Filled.Layers, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Jetzt slicen", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SliceDone(result: SliceResult, onPrint: () -> Unit, onUpload: () -> Unit, onLibrary: () -> Unit, onAgain: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = K1Green, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Fertig gesliced", style = MaterialTheme.typography.titleMedium)
                Text(result.gcode.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Druckzeit", result.printTime ?: "–", Icons.Filled.Schedule, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile(
                "Filament",
                listOfNotNull(
                    result.filamentGrams?.let { String.format(Locale.GERMANY, "%.0f g", it) },
                    result.filamentMeters?.let { String.format(Locale.GERMANY, "%.1f m", it) },
                ).joinToString(" · ").ifBlank { "–" },
                Icons.Filled.Scale,
                K1Blue,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPrint, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Drucken", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Icon(Icons.Filled.Upload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nur auf den Drucker laden")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onLibrary) { Text("Zu den Modellen") }
            TextButton(onClick = onAgain) { Text("Neu slicen") }
        }
    }
}

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
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
