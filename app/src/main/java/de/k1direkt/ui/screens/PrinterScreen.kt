package de.k1direkt.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.PrinterMode
import de.k1direkt.data.formatBytes
import de.k1direkt.printer.PrintState
import de.k1direkt.ui.AppViewModel

@Composable
fun PrinterScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val error by vm.statusError.collectAsStateWithLifecycle()
    val remoteFiles by vm.remoteFiles.collectAsStateWithLifecycle()
    var showCam by rememberSaveable { mutableStateOf(false) }
    var lightOn by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmEstop by remember { mutableStateOf(false) }
    var gcode by rememberSaveable { mutableStateOf("") }
    val isMoonraker = settings.printerMode == PrinterMode.MOONRAKER

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }
    LaunchedEffect(settings.printerHost, settings.printerMode) { vm.loadRemoteFiles() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Creality K1 Max", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (settings.printerConfigured) "${settings.printerHost} · ${settings.printerMode.label}" else "Noch nicht eingerichtet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (!settings.printerConfigured) {
            InfoCard(
                title = "Drucker verbinden",
                text = "Trage unter Einstellungen die IP-Adresse deines K1 Max ein (am Drucker: Einstellungen → Netzwerk). " +
                    "Empfohlen: Root aktivieren und Moonraker nutzen (Port 7125).",
            )
        } else {

        // ---------------- Status
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateBadge(status.state)
                    Spacer(Modifier.weight(1f))
                    if (status.state == PrintState.PRINTING || status.state == PrintState.PAUSED) {
                        Text("${(status.progress * 100).toInt()} %", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (status.state == PrintState.OFFLINE && error != null) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiOff, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                status.fileName?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (status.state == PrintState.PRINTING || status.state == PrintState.PAUSED) {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp),
                    )
                    Row {
                        Text("Vergangen: ${formatDuration(status.elapsedSeconds)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("Rest: ca. ${formatDuration(status.remainingSeconds)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                status.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row {
                    TempBox("Düse", formatTemp(status.nozzleTemp, status.nozzleTarget), Modifier.weight(1f))
                    TempBox("Bett", formatTemp(status.bedTemp, status.bedTarget), Modifier.weight(1f))
                    TempBox("Kammer", formatTemp(status.chamberTemp, null), Modifier.weight(1f))
                }
            }
        }

        // ---------------- Steuerung
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status.state) {
                PrintState.PRINTING -> FilledTonalButton(onClick = { vm.pause() }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Pause, null); Text(" Pause")
                }
                PrintState.PAUSED -> FilledTonalButton(onClick = { vm.resume() }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null); Text(" Weiter")
                }
                else -> Unit
            }
            if (status.state == PrintState.PRINTING || status.state == PrintState.PAUSED) {
                OutlinedButton(onClick = { confirmCancel = true }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Cancel, null); Text(" Abbrechen")
                }
            }
        }

        if (isMoonraker) {
            SectionTitle("Schnellaktionen")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AssistChip(onClick = { vm.sendGcode("G28") }, label = { Text("Home") }, leadingIcon = { Icon(Icons.Filled.Home, null) })
                AssistChip(
                    onClick = { vm.sendGcode("SET_HEATER_TEMPERATURE HEATER=extruder TARGET=220\nSET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=60") },
                    label = { Text("PLA vorheizen") },
                    leadingIcon = { Icon(Icons.Filled.LocalFireDepartment, null) },
                )
                AssistChip(onClick = { vm.sendGcode("TURN_OFF_HEATERS") }, label = { Text("Abkühlen") }, leadingIcon = { Icon(Icons.Filled.AcUnit, null) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LightMode, null)
                Text("  Beleuchtung", Modifier.weight(1f))
                Switch(checked = lightOn, onCheckedChange = {
                    lightOn = it
                    vm.sendGcode("SET_PIN PIN=LED VALUE=${if (it) 1 else 0}")
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = gcode,
                    onValueChange = { gcode = it },
                    label = { Text("G-Code / Makro") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (gcode.isNotBlank()) { vm.sendGcode(gcode.trim()); gcode = "" } }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Senden")
                }
            }
        }

        // ---------------- Kamera
        SectionTitle("Kamera")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Videocam, null)
            Text("  Live-Bild anzeigen", Modifier.weight(1f))
            Switch(checked = showCam, onCheckedChange = { showCam = it })
        }
        if (showCam && settings.effectiveWebcamUrl.isNotBlank()) {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                key(settings.effectiveWebcamUrl) {
                    MjpegView(settings.effectiveWebcamUrl, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                }
            }
            Text(settings.effectiveWebcamUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---------------- Dateien auf dem Drucker
        if (isMoonraker) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Dateien auf dem Drucker (${remoteFiles.size})", Modifier.weight(1f))
                IconButton(onClick = { vm.loadRemoteFiles() }) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
            }
            remoteFiles.take(50).forEach { f ->
                ListItem(
                    headlineContent = { Text(f.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${formatBytes(f.sizeBytes)} · ${formatDate(f.modified)}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.printRemote(f.path) }) { Icon(Icons.Filled.PlayArrow, "Drucken") }
                            IconButton(onClick = { vm.deleteRemote(f.path) }) { Icon(Icons.Filled.Delete, "Löschen") }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { confirmEstop = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PowerSettingsNew, null)
            Text("  NOT-AUS")
        }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Druck abbrechen?") },
            text = { Text("Der laufende Druck wird beendet und kann nicht fortgesetzt werden.") },
            confirmButton = { TextButton(onClick = { vm.cancel(); confirmCancel = false }) { Text("Abbrechen") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Weiterdrucken") } },
        )
    }
    if (confirmEstop) {
        AlertDialog(
            onDismissRequest = { confirmEstop = false },
            title = { Text("NOT-AUS auslösen?") },
            text = { Text("Klipper stoppt sofort alle Motoren und Heizungen. Danach ist ein Neustart der Firmware nötig.") },
            confirmButton = { TextButton(onClick = { vm.emergencyStop(); confirmEstop = false }) { Text("NOT-AUS") } },
            dismissButton = { TextButton(onClick = { confirmEstop = false }) { Text("Zurück") } },
        )
    }
}

@Composable
private fun StateBadge(state: PrintState) {
    val color = when (state) {
        PrintState.PRINTING -> Color(0xFF2E7D32)
        PrintState.PAUSED -> Color(0xFFF9A825)
        PrintState.COMPLETE -> Color(0xFF1565C0)
        PrintState.ERROR, PrintState.CANCELLED -> MaterialTheme.colorScheme.error
        PrintState.OFFLINE -> Color.Gray
        PrintState.STANDBY -> Color(0xFF546E7A)
    }
    Card(shape = RoundedCornerShape(8.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color)) {
        Text(state.label, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TempBox(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** MJPEG-Stream (Crowsnest/mjpg-streamer) – WebView rendert multipart/x-mixed-replace nativ. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MjpegView(url: String, modifier: Modifier) {
    val html = """
        <html><body style="margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh">
        <img src="$url" style="max-width:100%;max-height:100%" onerror="document.body.innerHTML='<p style=&quot;color:#bbb;font-family:sans-serif;padding:16px&quot;>Kamera nicht erreichbar.<br>URL in den Einstellungen prüfen.</p>'"/>
        </body></html>
    """.trimIndent()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.BLACK)
                loadDataWithBaseURL(url, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}
