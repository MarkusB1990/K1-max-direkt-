package de.k1direkt.ui.screens

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.PrinterMode
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.theme.K1Blue
import de.k1direkt.ui.theme.K1Purple

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    var s by remember { mutableStateOf(saved) }
    // Wenn die gespeicherten Werte (z. B. beim App-Start) geladen werden, das Formular aktualisieren
    LaunchedEffect(saved) { s = saved }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            PageHeader("Einstellungen")
            Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupCard("Drucker", Icons.Filled.Print, MaterialTheme.colorScheme.primary) {
                    Field("IP-Adresse des K1 Max", s.printerHost, "z. B. 192.168.178.50") { s = s.copy(printerHost = it) }
                    Hint("Am Drucker unter Einstellungen → Netzwerk.")
                    PrinterMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = s.printerMode == mode, onClick = { s = s.copy(printerMode = mode) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.printerMode == mode, onClick = { s = s.copy(printerMode = mode) })
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (s.printerMode == PrinterMode.MOONRAKER) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("Port", s.moonrakerPort.toString(), "7125", Modifier.weight(0.4f), KeyboardType.Number) {
                                s = s.copy(moonrakerPort = it.filter(Char::isDigit).toIntOrNull() ?: 7125)
                            }
                            Field("API-Key (optional)", s.moonrakerApiKey, "", Modifier.weight(0.6f), secret = true) { s = s.copy(moonrakerApiKey = it) }
                        }
                    } else {
                        Hint("Die Original-Firmware hat keine offizielle Schnittstelle – Hochladen kann scheitern. Zuverlässiger: Root + Moonraker.")
                    }
                    Field("Kamera-URL (optional)", s.webcamUrl, s.effectiveWebcamUrl.ifBlank { "http://…/webcam/?action=stream" }) { s = s.copy(webcamUrl = it) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Nach dem Hochladen direkt drucken", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = s.startAfterUpload, onCheckedChange = { s = s.copy(startAfterUpload = it) })
                    }
                }

                GroupCard("Slicer", Icons.Filled.Layers, K1Blue) {
                    Field("Server-Adresse", s.slicerUrl, "http://192.168.178.93:8090") { s = s.copy(slicerUrl = it) }
                    Field("Zugangs-Token (optional)", s.slicerToken, "", secret = true) { s = s.copy(slicerToken = it) }
                    Hint("OrcaSlicer mit den offiziellen K1-Max-Profilen – z. B. als Home-Assistant-App „K1 Max Slicer“.")
                    OutlinedButton(onClick = { vm.saveSettings(s); vm.testSlicer(s) }, shape = MaterialTheme.shapes.medium) {
                        Text("Verbindung testen")
                    }
                }

                GroupCard("Zugänge für die Suche", Icons.Filled.Key, K1Purple) {
                    Hint("Optional. Printables geht ohne Zugang; alle Seiten funktionieren auch im eingebauten Browser.")
                    Field("Thingiverse App-Token", s.thingiverseToken, "", secret = true) { s = s.copy(thingiverseToken = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Cults3D Benutzer", s.cultsUser, "", Modifier.weight(1f)) { s = s.copy(cultsUser = it) }
                        Field("Cults3D API-Key", s.cultsApiKey, "", Modifier.weight(1f), secret = true) { s = s.copy(cultsApiKey = it) }
                    }
                    Field("MyMiniFactory API-Key", s.myMiniFactoryKey, "", secret = true) { s = s.copy(myMiniFactoryKey = it) }
                }

                Text(
                    "K1 Max Direkt 0.2 · Modelle unterliegen den Lizenzen ihrer Urheber.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
        if (s != saved) {
            Button(
                onClick = { vm.saveSettings(s) },
                modifier = Modifier.fillMaxWidth().padding(ScreenPadding).height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text("Änderungen speichern") }
        }
    }
}

@Composable
private fun GroupCard(title: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Uri,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
        modifier = modifier,
    )
}
