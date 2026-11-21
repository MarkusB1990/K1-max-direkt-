package de.k1direkt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.k1direkt.slicer.Material
import de.k1direkt.slicer.SliceParams
import de.k1direkt.slicer.SupportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class PrinterMode(val label: String) {
    MOONRAKER("Moonraker / Klipper (Root, empfohlen)"),
    CREALITY_STOCK("Creality Original-Firmware (experimentell)")
}

data class AppSettings(
    val printerHost: String = "",
    val printerMode: PrinterMode = PrinterMode.MOONRAKER,
    val moonrakerPort: Int = 7125,
    val moonrakerApiKey: String = "",
    val webcamUrl: String = "",
    val slicerUrl: String = "",
    val slicerToken: String = "",
    val thingiverseToken: String = "",
    val cultsUser: String = "",
    val cultsApiKey: String = "",
    val myMiniFactoryKey: String = "",
    val defaultSlice: SliceParams = SliceParams(),
    val startAfterUpload: Boolean = true,
) {
    val printerConfigured get() = printerHost.isNotBlank()
    val slicerConfigured get() = slicerUrl.isNotBlank()

    /** Kamera-Adressen, die nacheinander probiert werden (eigene URL hat Vorrang). */
    fun webcamCandidates(reported: List<String> = emptyList()): List<String> {
        if (webcamUrl.isNotBlank()) return listOf(webcamUrl.trim())
        if (printerHost.isBlank()) return emptyList()
        val h = hostOnly(printerHost)
        return (reported + listOf(
            "http://$h:8080/?action=stream",          // Creality-Kamera (Original-Firmware und nach Root)
            "http://$h:4408/webcam/?action=stream",   // Fluidd (Helper Script)
            "http://$h:4409/webcam/?action=stream",   // Mainsail
            "http://$h/webcam/?action=stream",
        )).distinct()
    }
}

/** Entfernt Schema, Pfad und Port aus einer Eingabe wie "http://192.168.1.50:7125/". */
fun hostOnly(input: String): String =
    input.trim()
        .removePrefix("http://").removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')

class SettingsRepository(private val context: Context) {

    private object Keys {
        val printerHost = stringPreferencesKey("printer_host")
        val printerMode = stringPreferencesKey("printer_mode")
        val moonrakerPort = intPreferencesKey("moonraker_port")
        val moonrakerApiKey = stringPreferencesKey("moonraker_api_key")
        val webcamUrl = stringPreferencesKey("webcam_url")
        val slicerUrl = stringPreferencesKey("slicer_url")
        val slicerToken = stringPreferencesKey("slicer_token")
        val thingiverseToken = stringPreferencesKey("thingiverse_token")
        val cultsUser = stringPreferencesKey("cults_user")
        val cultsApiKey = stringPreferencesKey("cults_api_key")
        val mmfKey = stringPreferencesKey("mmf_key")
        val startAfterUpload = booleanPreferencesKey("start_after_upload")

        val layerHeight = floatPreferencesKey("slice_layer_height")
        val infill = intPreferencesKey("slice_infill")
        val support = stringPreferencesKey("slice_support")
        val material = stringPreferencesKey("slice_material")
        val brim = floatPreferencesKey("slice_brim")
        val nozzleTemp = intPreferencesKey("slice_nozzle_temp")
        val bedTemp = intPreferencesKey("slice_bed_temp")
        val perimeters = intPreferencesKey("slice_perimeters")
        val speed = intPreferencesKey("slice_speed")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = SliceParams()
        AppSettings(
            printerHost = p[Keys.printerHost] ?: "",
            printerMode = p[Keys.printerMode]?.let { runCatching { PrinterMode.valueOf(it) }.getOrNull() }
                ?: PrinterMode.MOONRAKER,
            moonrakerPort = p[Keys.moonrakerPort] ?: 7125,
            moonrakerApiKey = p[Keys.moonrakerApiKey] ?: "",
            webcamUrl = p[Keys.webcamUrl] ?: "",
            slicerUrl = p[Keys.slicerUrl] ?: "",
            slicerToken = p[Keys.slicerToken] ?: "",
            thingiverseToken = p[Keys.thingiverseToken] ?: "",
            cultsUser = p[Keys.cultsUser] ?: "",
            cultsApiKey = p[Keys.cultsApiKey] ?: "",
            myMiniFactoryKey = p[Keys.mmfKey] ?: "",
            startAfterUpload = p[Keys.startAfterUpload] ?: true,
            defaultSlice = SliceParams(
                layerHeight = p[Keys.layerHeight] ?: d.layerHeight,
                infillPercent = p[Keys.infill] ?: d.infillPercent,
                support = p[Keys.support]?.let { runCatching { SupportMode.valueOf(it) }.getOrNull() } ?: d.support,
                material = p[Keys.material]?.let { runCatching { Material.valueOf(it) }.getOrNull() } ?: d.material,
                brimWidth = p[Keys.brim] ?: d.brimWidth,
                nozzleTemp = p[Keys.nozzleTemp] ?: d.nozzleTemp,
                bedTemp = p[Keys.bedTemp] ?: d.bedTemp,
                perimeters = p[Keys.perimeters] ?: d.perimeters,
                speedPercent = p[Keys.speed] ?: d.speedPercent,
            ),
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.printerHost] = s.printerHost.trim()
            p[Keys.printerMode] = s.printerMode.name
            p[Keys.moonrakerPort] = s.moonrakerPort
            p[Keys.moonrakerApiKey] = s.moonrakerApiKey.trim()
            p[Keys.webcamUrl] = s.webcamUrl.trim()
            p[Keys.slicerUrl] = s.slicerUrl.trim().trimEnd('/')
            p[Keys.slicerToken] = s.slicerToken.trim()
            p[Keys.thingiverseToken] = s.thingiverseToken.trim()
            p[Keys.cultsUser] = s.cultsUser.trim()
            p[Keys.cultsApiKey] = s.cultsApiKey.trim()
            p[Keys.mmfKey] = s.myMiniFactoryKey.trim()
            p[Keys.startAfterUpload] = s.startAfterUpload
            val sl = s.defaultSlice
            p[Keys.layerHeight] = sl.layerHeight
            p[Keys.infill] = sl.infillPercent
            p[Keys.support] = sl.support.name
            p[Keys.material] = sl.material.name
            p[Keys.brim] = sl.brimWidth
            p[Keys.nozzleTemp] = sl.nozzleTemp
            p[Keys.bedTemp] = sl.bedTemp
            p[Keys.perimeters] = sl.perimeters
            p[Keys.speed] = sl.speedPercent
        }
    }
}
