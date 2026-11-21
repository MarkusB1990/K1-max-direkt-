package de.k1direkt.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.FileType
import de.k1direkt.data.LibraryFile
import de.k1direkt.data.formatBytes
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.Overlay

private enum class LibFilter(val label: String) { ALL("Alle"), MODELS("Modelle"), GCODE("G-Code"), OWN("Eigene") }

@Composable
fun LibraryScreen(vm: AppViewModel, nav: Navigator) {
    val files by vm.library.files.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LibFilter.ALL) }
    var renameItem by remember { mutableStateOf<LibraryFile?>(null) }
    var deleteItem by remember { mutableStateOf<LibraryFile?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris)
    }

    val shown = files.filter {
        when (filter) {
            LibFilter.ALL -> true
            LibFilter.MODELS -> it.type.sliceable
            LibFilter.GCODE -> it.type.printable
            LibFilter.OWN -> it.isOwn
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Bibliothek", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${files.size} Dateien · Downloads, eigene Modelle und gesliced G-Code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LibFilter.entries) { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    InfoCard(
                        title = "Noch keine Dateien",
                        text = "Lade Modelle über „Entdecken“ herunter, teile STL/3MF-Dateien aus anderen Apps mit K1 Max Direkt " +
                            "oder tippe unten auf „Eigene Datei“, um selbst erstellte Modelle hochzuladen.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(shown, key = { it.file.absolutePath }) { item ->
                LibraryRow(
                    item = item,
                    onOpen = {
                        when {
                            item.type.sliceable && item.type != FileType.STEP -> nav.open(Overlay.Viewer(item))
                            item.type.sliceable -> nav.open(Overlay.Slice(item))
                            item.type.printable -> vm.sendToPrinter(item, start = vm.settings.value.startAfterUpload)
                        }
                    },
                    onSlice = { nav.open(Overlay.Slice(item)) },
                    onPreview = { nav.open(Overlay.Viewer(item)) },
                    onPrint = { vm.sendToPrinter(item, start = true) },
                    onUploadOnly = { vm.sendToPrinter(item, start = false) },
                    onSource = { item.sourceUrl?.let { nav.open(Overlay.Browser(it)) } },
                    onRename = { renameItem = item },
                    onDelete = { deleteItem = item },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            icon = { Icon(Icons.Filled.UploadFile, null) },
            text = { Text("Eigene Datei") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    renameItem?.let { item ->
        var name by remember(item) { mutableStateOf(item.name.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text("Umbenennen") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.rename(item, name); renameItem = null }, enabled = name.isNotBlank()) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { renameItem = null }) { Text("Abbrechen") } },
        )
    }
    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Löschen?") },
            text = { Text("„${item.name}“ wird vom Handy gelöscht.") },
            confirmButton = { TextButton(onClick = { vm.delete(item); deleteItem = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun LibraryRow(
    item: LibraryFile,
    onOpen: () -> Unit,
    onSlice: () -> Unit,
    onPreview: () -> Unit,
    onPrint: () -> Unit,
    onUploadOnly: () -> Unit,
    onSource: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = {
            Icon(
                when {
                    item.type.printable -> Icons.Filled.Description
                    else -> Icons.Filled.ViewInAr
                },
                contentDescription = null,
                tint = if (item.type.printable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${item.type.label} · ${formatBytes(item.sizeBytes)} · ${item.source} · ${formatDate(item.addedAt)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.type.sliceable) {
                    IconButton(onClick = onSlice) { Icon(Icons.Filled.Layers, "Slicen") }
                }
                if (item.type.printable) {
                    IconButton(onClick = onPrint) { Icon(Icons.Filled.PlayArrow, "Drucken") }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (item.type.sliceable && item.type != FileType.STEP) {
                            DropdownMenuItem(text = { Text("3D-Vorschau") }, leadingIcon = { Icon(Icons.Filled.ViewInAr, null) },
                                onClick = { menu = false; onPreview() })
                        }
                        if (item.type.sliceable) {
                            DropdownMenuItem(text = { Text("Slicen") }, leadingIcon = { Icon(Icons.Filled.Layers, null) },
                                onClick = { menu = false; onSlice() })
                        }
                        if (item.type.printable) {
                            DropdownMenuItem(text = { Text("Senden & drucken") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                                onClick = { menu = false; onPrint() })
                            DropdownMenuItem(text = { Text("Nur auf Drucker laden") }, leadingIcon = { Icon(Icons.Filled.Upload, null) },
                                onClick = { menu = false; onUploadOnly() })
                        }
                        if (item.sourceUrl != null) {
                            DropdownMenuItem(text = { Text("Quelle öffnen") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = { menu = false; onSource() })
                        }
                        DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
    )
}
