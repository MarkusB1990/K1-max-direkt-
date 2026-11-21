package de.k1direkt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.k1direkt.data.PrinterMode
import de.k1direkt.ui.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    var s by remember { mutableStateOf(saved) }
    // Wenn die gespeicherten Werte (z. B. beim App-Start) geladen werden, das Formular aktualisieren
    LaunchedEffect(saved) { s = saved }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ------------------------------------------------ Drucker
        SectionTitle("Drucker (Creality K1 Max)")
        Field("IP-Adresse des Druckers", s.printerHost, "z. B. 192.168.178.50") { s = s.copy(printerHost = it) }
        Text(
            "Die IP findest du am Drucker unter Einstellungen → Netzwerk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PrinterMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = s.printerMode == mode, onClick = { s = s.copy(printerMode = mode) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.printerMode == mode, onClick = { s = s.copy(printerMode = mode) })
                Text(mode.label)
            }
        }
        if (s.printerMode == PrinterMode.MOONRAKER) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Moonraker-Port", s.moonrakerPort.toString(), "7125", Modifier.weight(1f), KeyboardType.Number) {
                    s = s.copy(moonrakerPort = it.filter(Char::isDigit).toIntOrNull() ?: 7125)
                }
                Field("API-Key (optional)", s.moonrakerApiKey, "", Modifier.weight(1f), secret = true) {
                    s = s.copy(moonrakerApiKey = it)
                }
            }
        } else {
            Text(
                "Die Original-Firmware hat keine offizielle Schnittstelle. Status, Pause und Abbrechen laufen über " +
                    "den Creality-WebSocket (Port 9999); das Hochladen kann je nach Firmware-Version scheitern. " +
                    "Zuverlässiger: am Drucker Root aktivieren und Moonraker installieren (Creality Helper Script).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Field("Kamera-URL (optional)", s.webcamUrl, s.effectiveWebcamUrl.ifBlank { "http://…/webcam/?action=stream" }) {
            s = s.copy(webcamUrl = it)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Nach dem Hochladen automatisch drucken", Modifier.weight(1f))
            Switch(checked = s.startAfterUpload, onCheckedChange = { s = s.copy(startAfterUpload = it) })
        }

        // ------------------------------------------------ Slicer
        SectionTitle("Slicer-Server")
        Text(
            "Slicen übernimmt der mitgelieferte Slicer-Server (OrcaSlicer mit den offiziellen K1-Max-Profilen) – " +
                "als Home-Assistant-App oder Docker-Container. Anleitung: slicer-server/README.md.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field("Server-Adresse", s.slicerUrl, "http://192.168.178.10:8090") { s = s.copy(slicerUrl = it) }
        Field("Zugangs-Token (optional)", s.slicerToken, "", secret = true) { s = s.copy(slicerToken = it) }
        OutlinedButton(
            onClick = { vm.saveSettings(s); vm.testSlicer(s) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Speichern & Verbindung testen") }

        // ------------------------------------------------ Modellseiten
        SectionTitle("Zugänge für die Suche (optional)")
        Text(
            "Printables funktioniert ohne Zugang. Für Thingiverse, Cults3D und MyMiniFactory brauchst du einen kostenlosen " +
                "API-Schlüssel – ohne Schlüssel kannst du diese Seiten trotzdem im eingebauten Browser nutzen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field("Thingiverse App-Token", s.thingiverseToken, "thingiverse.com/developers → App erstellen", secret = true) {
            s = s.copy(thingiverseToken = it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Cults3D Benutzer", s.cultsUser, "", Modifier.weight(1f)) { s = s.copy(cultsUser = it) }
            Field("Cults3D API-Key", s.cultsApiKey, "cults3d.com/de/api/keys", Modifier.weight(1f), secret = true) {
                s = s.copy(cultsApiKey = it)
            }
        }
        Field("MyMiniFactory API-Key", s.myMiniFactoryKey, "myminifactory.com/settings/developer", secret = true) {
            s = s.copy(myMiniFactoryKey = it)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.saveSettings(s) },
            enabled = s != saved,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Speichern") }

        Spacer(Modifier.height(24.dp))
        Text(
            "K1 Max Direkt 0.1 · Modelle unterliegen den Lizenzen ihrer Urheber (z. B. Creative Commons). " +
                "Bitte Lizenzbedingungen der jeweiligen Plattform beachten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
        modifier = modifier.padding(top = 4.dp),
    )
}

