package de.k1direkt.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.k1direkt.data.LibraryFile
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.Overlay
import de.k1direkt.ui.theme.K1Purple
import de.k1direkt.viewer.ThreeMfProject
import de.k1direkt.viewer.TmfObject
import de.k1direkt.viewer.TmfPlate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private sealed interface ProjectState {
    data object Loading : ProjectState
    data class Ready(val project: ThreeMfProject) : ProjectState
    data class Failed(val message: String) : ProjectState
}

private enum class TmfTab { PLATES, PARTS }

/**
 * Inhalt einer 3MF-Datei: Platten (mit Vorschaubild) und Einzelteile.
 * Gewählte Platten/Teile werden als eigene STL gespeichert und direkt gesliced.
 */
@Composable
fun ThreeMfScreen(vm: AppViewModel, nav: Navigator, item: LibraryFile) {
    var state by remember(item) { mutableStateOf<ProjectState>(ProjectState.Loading) }
    LaunchedEffect(item) {
        state = withContext(Dispatchers.Default) {
            try {
                ProjectState.Ready(ThreeMfProject.read(item.file))
            } catch (e: OutOfMemoryError) {
                ProjectState.Failed("Die Datei ist zu groß, um sie auf dem Handy aufzuteilen.")
            } catch (e: Exception) {
                ProjectState.Failed(e.message ?: "3MF konnte nicht gelesen werden")
            }
        }
    }
    var preview by remember { mutableStateOf<Pair<String, List<Int>>?>(null) }
    val ready = state as? ProjectState.Ready
    var tab by rememberSaveable { mutableStateOf(TmfTab.PLATES) }

    fun slice(project: ThreeMfProject, indices: List<Int>, label: String) {
        vm.extract3mf(item, project, indices, label) { stl -> nav.replace(Overlay.Slice(stl)) }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(
            title = item.name,
            subtitle = ready?.let { "${it.project.plates.size} Platte(n) · ${it.project.objects.size} Teil(e)" } ?: "3MF-Projekt",
            onBack = { nav.back() },
        )
        when (val st = state) {
            ProjectState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Projekt wird gelesen …", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ProjectState.Failed -> Column(Modifier.padding(ScreenPadding)) {
                InfoCard("3MF konnte nicht aufgeteilt werden", st.message, icon = Icons.Filled.Warning)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { nav.replace(Overlay.Slice(item)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Trotzdem komplett slicen")
                }
            }
            is ProjectState.Ready -> {
                val project = st.project
                val multiPlate = project.plates.size > 1
                LazyColumn(
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "Wähle, was gedruckt werden soll. Die Auswahl wird als eigene Datei unter „Modelle“ gespeichert und gesliced.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        if (multiPlate) {
                            Segmented(
                                options = TmfTab.entries,
                                selected = tab,
                                label = { if (it == TmfTab.PLATES) "Platten · ${project.plates.size}" else "Einzelteile · ${project.objects.size}" },
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) { tab = it }
                        }
                    }
                    if (multiPlate && tab == TmfTab.PLATES) {
                        items(project.plates, key = { "p${it.number}" }) { plate ->
                            PlateCard(
                                item = item,
                                plate = plate,
                                parts = plate.itemIndices.mapNotNull { i -> project.objects.firstOrNull { it.itemIndex == i } },
                                onPreview = { preview = plate.name to plate.itemIndices },
                                onSlice = { slice(project, plate.itemIndices, plate.name) },
                            )
                        }
                    } else {
                        if (!multiPlate && project.objects.size > 1) {
                            item {
                                PlateCard(
                                    item = item,
                                    plate = project.plates.first().copy(name = "Alle Teile zusammen"),
                                    parts = project.objects,
                                    onPreview = { preview = "Alle Teile" to project.objects.map { it.itemIndex } },
                                    onSlice = { slice(project, project.objects.map { it.itemIndex }, "Alle Teile") },
                                )
                            }
                            item { SectionTitle("Oder ein einzelnes Teil") }
                        }
                        items(project.objects, key = { "o${it.itemIndex}" }) { obj ->
                            PartRow(
                                obj = obj,
                                onPreview = { preview = obj.name to listOf(obj.itemIndex) },
                                onSlice = { slice(project, listOf(obj.itemIndex), obj.name) },
                            )
                        }
                    }
                }
                preview?.let { (title, indices) ->
                    PartPreviewDialog(project, title, indices) { preview = null }
                }
            }
        }
    }
}

@Composable
private fun PlateCard(item: LibraryFile, plate: TmfPlate, parts: List<TmfObject>, onPreview: () -> Unit, onSlice: () -> Unit) {
    var thumb by remember(item, plate.thumbnail) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item, plate.thumbnail) {
        thumb = plate.thumbnail?.let { path ->
            withContext(Dispatchers.IO) {
                ThreeMfProject.readEntry(item.file, path)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
            }
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val t = thumb
                if (t != null) Image(t, plate.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                else Icon(Icons.Filled.Widgets, null, tint = K1Purple, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(plate.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (parts.size == 1) parts.first().name else "${parts.size} Teile: " + parts.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onSlice, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Icon(Icons.Filled.Layers, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Slicen")
                    }
                    OutlinedButton(onClick = onPreview, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Filled.Visibility, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartRow(obj: TmfObject, onPreview: () -> Unit, onSlice: () -> Unit) {
    val tooBig = obj.sizeX > 300 || obj.sizeY > 300 || obj.sizeZ > 300
    Surface(onClick = onPreview, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.ViewInAr, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(obj.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    String.format(Locale.GERMANY, "%.0f × %.0f × %.0f mm", obj.sizeX, obj.sizeY, obj.sizeZ) +
                        if (tooBig) " · zu groß für den K1 Max" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSlice) { Icon(Icons.Filled.Layers, "Slicen", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun PartPreviewDialog(project: ThreeMfProject, title: String, indices: List<Int>, onDismiss: () -> Unit) {
    var mesh by remember(indices) { mutableStateOf<MeshState>(MeshState.Loading) }
    LaunchedEffect(indices) {
        mesh = withContext(Dispatchers.Default) {
            try { MeshState.Ready(project.mesh(indices, maxTriangles = 1_000_000)) } catch (e: Exception) { MeshState.Failed(e.message ?: "Fehler") }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column {
                Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Schließen") }
                }
                ModelPreview(mesh, Modifier.fillMaxWidth().height(360.dp))
                (mesh as? MeshState.Ready)?.mesh?.let {
                    Text(it.dimensionText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
