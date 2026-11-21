package de.k1direkt.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.PrinterMode
import de.k1direkt.data.formatBytes
import de.k1direkt.printer.PrintState
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.theme.K1Blue
import de.k1direkt.ui.theme.K1Green
import de.k1direkt.ui.theme.K1Orange
import de.k1direkt.ui.theme.K1Purple

@Composable
fun PrinterScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val error by vm.statusError.collectAsStateWithLifecycle()
    val remoteFiles by vm.remoteFiles.collectAsStateWithLifecycle()
    var showCam by rememberSaveable { mutableStateOf(false) }
    var lightOn by rememberSaveable { mutableStateOf(false) }
    var tools by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmEstop by remember { mutableStateOf(false) }
    var gcode by rememberSaveable { mutableStateOf("") }
    val isMoonraker = settings.printerMode == PrinterMode.MOONRAKER
    val active = status.state == PrintState.PRINTING || status.state == PrintState.PAUSED

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }
    LaunchedEffect(settings.printerHost, settings.printerMode) { vm.loadRemoteFiles() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("K1 Max", if (settings.printerConfigured) settings.printerHost else "Noch nicht verbunden") {
            StateDot(status.state)
        }
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!settings.printerConfigured) {
                EmptyState(
                    Icons.Filled.Print,
                    "Drucker verbinden",
                    "Trage unter Einstellungen die IP-Adresse deines K1 Max ein (am Drucker: Einstellungen → Netzwerk).",
                )
            } else {

            // ---------- Hauptkarte
            SectionCard(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (active || status.state == PrintState.COMPLETE) status.progress.coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.size(116.dp),
                            strokeWidth = 10.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                        )
                        if (active || status.state == PrintState.COMPLETE) {
                            Text("${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Icon(
                                if (status.state == PrintState.OFFLINE) Icons.Filled.WifiOff else Icons.Filled.Print,
                                null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.state.label, style = MaterialTheme.typography.titleLarge, color = stateColor(status.state))
                        Text(
                            status.fileName ?: when (status.state) {
                                PrintState.OFFLINE -> error ?: "Keine Verbindung"
                                else -> "Bereit für den nächsten Druck"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (active) {
                            Spacer(Modifier.height(6.dp))
                            Text("noch ca. ${formatDuration(status.remainingSeconds)}", style = MaterialTheme.typography.titleSmall)
                            Text("läuft seit ${formatDuration(status.elapsedSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (active) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (status.state == PrintState.PRINTING) {
                            FilledTonalButton(onClick = { vm.pause() }, Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause")
                            }
                        } else {
                            Button(onClick = { vm.resume() }, Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Weiter")
                            }
                        }
                        OutlinedButton(onClick = { confirmCancel = true }, Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Filled.Close, null); Spacer(Modifier.width(6.dp)); Text("Abbrechen")
                        }
                    }
                }
            }

            // ---------- Temperaturen
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Düse", formatTemp(status.nozzleTemp, status.nozzleTarget), Icons.Filled.LocalFireDepartment, K1Orange, Modifier.weight(1f))
                StatTile("Bett", formatTemp(status.bedTemp, status.bedTarget), Icons.Filled.Thermostat, K1Purple, Modifier.weight(1f))
                StatTile("Kammer", formatTemp(status.chamberTemp, null), Icons.Filled.ViewInAr, K1Blue, Modifier.weight(1f))
            }

            // ---------- Kamera
            SectionCard(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().clickable { showCam = !showCam }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Videocam, K1Green, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Kamera", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = showCam, onCheckedChange = { showCam = it })
                }
                if (showCam) {
                    var candidates by remember(settings.webcamUrl, settings.printerHost) { mutableStateOf<List<String>?>(null) }
                    LaunchedEffect(settings.webcamUrl, settings.printerHost) { candidates = vm.webcamCandidates() }
                    val list = candidates
                    if (list == null) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (list.isNotEmpty()) {
                        key(list) {
                            MjpegView(
                                list,
                                Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(start = 12.dp, end = 12.dp, bottom = 12.dp).clip(MaterialTheme.shapes.medium),
                            )
                        }
                    }
                }
            }

            // ---------- Werkzeuge (nur Moonraker)
            if (isMoonraker) {
                SectionCard(padding = 0.dp) {
                    Row(Modifier.fillMaxWidth().clickable { tools = !tools }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Home, MaterialTheme.colorScheme.primary, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Steuerung", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (tools) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                    AnimatedVisibility(tools) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolChip("Home", Icons.Filled.Home) { vm.sendGcode("G28") }
                                ToolChip("PLA vorheizen", Icons.Filled.LocalFireDepartment) {
                                    vm.sendGcode("SET_HEATER_TEMPERATURE HEATER=extruder TARGET=220\nSET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=60")
                                }
                                ToolChip("Abkühlen", Icons.Filled.AcUnit) { vm.sendGcode("TURN_OFF_HEATERS") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LightMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("  Licht", Modifier.weight(1f))
                                Switch(checked = lightOn, onCheckedChange = {
                                    lightOn = it
                                    vm.sendGcode("SET_PIN PIN=LED VALUE=${if (it) 1 else 0}")
                                })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = gcode,
                                    onValueChange = { gcode = it },
                                    placeholder = { Text("G-Code / Makro") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { if (gcode.isNotBlank()) { vm.sendGcode(gcode.trim()); gcode = "" } }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Senden", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // ---------- Dateien auf dem Drucker
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Auf dem Drucker · ${remoteFiles.size}", Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(onClick = { vm.loadRemoteFiles() }) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                }
                remoteFiles.take(30).forEach { f ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Description, K1Blue, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${formatBytes(f.sizeBytes)} · ${formatDate(f.modified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.printRemote(f.path) }, enabled = !active) { Icon(Icons.Filled.PlayArrow, "Drucken") }
                            IconButton(onClick = { vm.deleteRemote(f.path) }) { Icon(Icons.Filled.Delete, "Löschen") }
                        }
                    }
                }
            }

            TextButton(
                onClick = { confirmEstop = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("NOT-AUS", color = MaterialTheme.colorScheme.error)
            }
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
            confirmButton = { TextButton(onClick = { vm.emergencyStop(); confirmEstop = false }) { Text("NOT-AUS", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEstop = false }) { Text("Zurück") } },
        )
    }
}

@Composable
private fun stateColor(state: PrintState): Color = when (state) {
    PrintState.PRINTING -> K1Green
    PrintState.PAUSED -> Color(0xFFFFC94D)
    PrintState.COMPLETE -> K1Blue
    PrintState.ERROR, PrintState.CANCELLED -> MaterialTheme.colorScheme.error
    PrintState.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
    PrintState.STANDBY -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun StateDot(state: PrintState) {
    Row(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (state == PrintState.OFFLINE) MaterialTheme.colorScheme.error else K1Green))
        Spacer(Modifier.width(6.dp))
        Text(if (state == PrintState.OFFLINE) "Offline" else "Online", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(50),
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = null,
    )
}

/**
 * MJPEG-Stream (Creality-Kamera / mjpg-streamer). Probiert die Adressen der Reihe nach,
 * bis eine ein Bild liefert. WebView rendert multipart/x-mixed-replace nativ.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MjpegView(urls: List<String>, modifier: Modifier) {
    val list = urls.joinToString(",") { "'" + it.replace("'", "%27") + "'" }
    val html = """
        <html><head><meta name="viewport" content="width=device-width"></head>
        <body style="margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh;color:#bbb;font-family:sans-serif">
        <img id="c" style="max-width:100%;max-height:100%;display:none"/>
        <p id="m" style="padding:16px;text-align:center;font-size:14px">Suche Kamera …</p>
        <script>
          var u=[$list], i=0, img=document.getElementById('c'), m=document.getElementById('m');
          function next(){
            if(i>=u.length){ img.style.display='none'; m.style.display='block';
              m.innerHTML='Kamera nicht gefunden.<br>Unter Einstellungen eine Kamera-URL eintragen.'; return; }
            m.innerHTML='Verbinde mit<br>'+u[i]; img.src=u[i++];
          }
          img.onerror=next;
          img.onload=function(){ img.style.display='block'; m.style.display='none'; };
          // MJPEG: sobald das erste Bild Größe hat, gilt die Adresse als gefunden
          setInterval(function(){ if(img.naturalWidth>0){ img.style.display='block'; m.style.display='none'; } }, 500);
          next();
        </script></body></html>
    """.trimIndent()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.BLACK)
                loadDataWithBaseURL("http://localhost/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}
