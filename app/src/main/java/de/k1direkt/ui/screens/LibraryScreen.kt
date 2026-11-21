package de.k1direkt.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.FileType
import de.k1direkt.data.LibraryFile
import de.k1direkt.data.formatBytes
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator
import de.k1direkt.ui.Overlay
import de.k1direkt.ui.theme.K1Blue
import de.k1direkt.ui.theme.K1Orange
import de.k1direkt.ui.theme.K1Purple

private enum class LibFilter(val label: String) { ALL("Alle"), MODELS("3D-Modelle"), GCODE("Druckfertig"), OWN("Eigene") }

private fun typeIcon(t: FileType): ImageVector = when (t) {
    FileType.GCODE -> Icons.Filled.Description
    FileType.THREE_MF -> Icons.Filled.Widgets
    else -> Icons.Filled.ViewInAr
}

private fun typeColor(t: FileType): Color = when (t) {
    FileType.GCODE -> K1Blue
    FileType.THREE_MF -> K1Purple
    else -> K1Orange
}

/** Was passiert beim Antippen einer Datei. */
fun openLibraryFile(item: LibraryFile, nav: Navigator, onPrint: (LibraryFile) -> Unit) {
    when (item.type) {
        FileType.THREE_MF -> nav.open(Overlay.ThreeMf(item))
        FileType.STL, FileType.OBJ -> nav.open(Overlay.Viewer(item))
        FileType.STEP -> nav.open(Overlay.Slice(item))
        FileType.GCODE -> onPrint(item)
        FileType.OTHER -> Unit
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel, nav: Navigator) {
    val files by vm.library.files.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LibFilter.ALL) }
    var renameItem by remember { mutableStateOf<LibraryFile?>(null) }
    var deleteItem by remember { mutableStateOf<LibraryFile?>(null) }
    var printItem by remember { mutableStateOf<LibraryFile?>(null) }

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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PageHeader("Modelle", "${files.size} Dateien", horizontalPadding = 4.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    items(LibFilter.entries) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Inventory2,
                        if (files.isEmpty()) "Noch keine Modelle" else "Nichts in dieser Ansicht",
                        "Lade Modelle unter „Entdecken“ herunter, teile Dateien aus anderen Apps " +
                            "oder lade eigene STL-, 3MF- und G-Code-Dateien hoch.",
                        action = { FilledTonalButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Datei hochladen") } },
                    )
                }
            }
            items(shown, key = { it.file.absolutePath }) { item ->
                LibraryRow(
                    item = item,
                    onOpen = { openLibraryFile(item, nav) { printItem = it } },
                    onPrimary = {
                        when {
                            item.type == FileType.THREE_MF -> nav.open(Overlay.ThreeMf(item))
                            item.type.sliceable -> nav.open(Overlay.Slice(item))
                            item.type.printable -> printItem = item
                        }
                    },
                    onPreview = { nav.open(Overlay.Viewer(item)) },
                    onUploadOnly = { vm.sendToPrinter(item, start = false) },
                    onSource = { item.sourceUrl?.let { nav.open(Overlay.Browser(it)) } },
                    onRename = { renameItem = item },
                    onDelete = { deleteItem = item },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Hochladen") },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    renameItem?.let { item ->
        var name by remember(item) { mutableStateOf(item.name.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text("Umbenennen") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(item, name); renameItem = null }, enabled = name.isNotBlank()) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renameItem = null }) { Text("Abbrechen") } },
        )
    }
    printItem?.let { item -> PrintDialog(vm, item) { printItem = null } }
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

/** Sicherheitsabfrage vor dem Senden an den Drucker. */
@Composable
fun PrintDialog(vm: AppViewModel, item: LibraryFile, onDismiss: () -> Unit) {
    val autoStart = vm.settings.value.startAfterUpload
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.PlayArrow, null) },
        title = { Text(if (autoStart) "Jetzt drucken?" else "An Drucker senden?") },
        text = {
            Text("„${item.name}“ wird auf den K1 Max geladen" + if (autoStart) " und sofort gestartet. Ist die Druckplatte frei?" else ".")
        },
        confirmButton = {
            TextButton(onClick = { vm.sendToPrinter(item, start = autoStart); onDismiss() }) { Text(if (autoStart) "Drucken" else "Senden") }
        },
        dismissButton = {
            Row {
                if (autoStart) TextButton(onClick = { vm.sendToPrinter(item, start = false); onDismiss() }) { Text("Nur laden") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun LibraryRow(
    item: LibraryFile,
    onOpen: () -> Unit,
    onPrimary: () -> Unit,
    onPreview: () -> Unit,
    onUploadOnly: () -> Unit,
    onSource: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(typeIcon(item.type), typeColor(item.type))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.type.label} · ${formatBytes(item.sizeBytes)} · ${item.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                item.type.sliceable -> IconButton(onClick = onPrimary) {
                    Icon(Icons.Filled.Layers, "Slicen", tint = MaterialTheme.colorScheme.primary)
                }
                item.type.printable -> IconButton(onClick = onPrimary) {
                    Icon(Icons.Filled.PlayArrow, "Drucken", tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (item.type in setOf(FileType.STL, FileType.OBJ, FileType.THREE_MF)) {
                        DropdownMenuItem(text = { Text("3D-Vorschau") }, leadingIcon = { Icon(Icons.Filled.ViewInAr, null) },
                            onClick = { menu = false; onPreview() })
                    }
                    if (item.type.printable) {
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
    }
}
