package de.k1direkt.slicer

import de.k1direkt.data.Http
import de.k1direkt.data.await
import de.k1direkt.data.getText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

data class SliceResult(
    val gcode: File,
    val printTime: String?,
    val filamentMeters: Double?,
    val filamentGrams: Double?,
)

/**
 * Spricht den mitgelieferten Slicer-Server (Ordner slicer-server/ im Repository) an.
 * Android selbst kann OrcaSlicer nicht ausführen – deshalb wird das Modell
 * an einen kleinen Server im Heimnetz geschickt (PC, NAS, Raspberry Pi, Home-Assistant-Host).
 */
class SlicerClient(baseUrl: String, private val token: String) {

    private val baseUrl = baseUrl.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }

    private fun Request.Builder.auth(): Request.Builder =
        if (token.isNotBlank()) header("Authorization", "Bearer $token") else this

    suspend fun health(): String {
        val req = Request.Builder().url("$baseUrl/health").auth().get().build()
        val json = JSONObject(Http.client.getText(req))
        return json.optString("slicer", "unbekannt")
    }

    suspend fun slice(model: File, params: SliceParams, output: File): SliceResult = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", model.name, model.asRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("layer_height", fmt(params.layerHeight))
            .addFormDataPart("infill", params.infillPercent.toString())
            .addFormDataPart("support", params.support.name.lowercase(Locale.ROOT))
            .addFormDataPart("material", params.material.name)
            .addFormDataPart("brim", fmt(params.brimWidth))
            .addFormDataPart("nozzle_temp", params.effectiveNozzleTemp.toString())
            .addFormDataPart("bed_temp", params.effectiveBedTemp.toString())
            .addFormDataPart("fan", params.material.fanPercent.toString())
            .addFormDataPart("perimeters", params.perimeters.toString())
            .addFormDataPart("speed", params.speedPercent.toString())
            .addFormDataPart("scale", params.scalePercent.toString())
            .addFormDataPart("rotate", params.rotateZ.toString())
            .build()
        val req = Request.Builder().url("$baseUrl/slice").auth().post(body).build()
        Http.longClient.await(req).use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string().orEmpty()
                val detail = runCatching { JSONObject(msg).optString("detail") }.getOrNull()?.ifBlank { null }
                throw IOException("Slicen fehlgeschlagen (HTTP ${resp.code}): ${detail ?: msg.take(400)}")
            }
            val src = resp.body ?: throw IOException("Leere Antwort vom Slicer-Server")
            output.outputStream().use { out -> src.byteStream().copyTo(out) }
            SliceResult(
                gcode = output,
                printTime = resp.header("X-Print-Time"),
                filamentMeters = resp.header("X-Filament-Meters")?.toDoubleOrNull(),
                filamentGrams = resp.header("X-Filament-Grams")?.toDoubleOrNull(),
            )
        }
    }

    private fun fmt(f: Float) = String.format(Locale.ROOT, "%.2f", f)
}

/** Liest Druckzeit und Filamentverbrauch aus Kommentaren einer G-Code-Datei. */
object GcodeInfo {
    data class Info(val printTime: String?, val filament: String?, val layerHeight: String?, val slicer: String?)

    fun read(file: File): Info {
        var time: String? = null
        var filament: String? = null
        var layer: String? = null
        var slicer: String? = null
        fun scan(lines: Sequence<String>) {
            for (l in lines) {
                if (!l.startsWith(";")) continue
                val line = l.trimStart(';', ' ')
                when {
                    time == null && line.startsWith("estimated printing time (normal mode)") -> time = line.substringAfter("=").trim()
                    time == null && line.startsWith("TIME:") -> time = formatSeconds(line.substringAfter(":").trim().toLongOrNull())
                    time == null && line.startsWith("estimated printing time") -> time = line.substringAfter("=").substringAfter(":").trim()
                    filament == null && line.startsWith("filament used [g]") -> filament = line.substringAfter("=").trim() + " g"
                    filament == null && line.startsWith("Filament used:") -> filament = line.substringAfter(":").trim()
                    layer == null && line.startsWith("layer_height =") -> layer = line.substringAfter("=").trim() + " mm"
                    slicer == null && line.startsWith("generated by") -> slicer = line.removePrefix("generated by").trim().substringBefore(" on ")
                    slicer == null && line.startsWith("Generated with") -> slicer = line.removePrefix("Generated with").trim()
                }
            }
        }
        runCatching {
            file.bufferedReader().use { r -> scan(r.lineSequence().take(400)) }
            // OrcaSlicer/PrusaSlicer schreiben die Zusammenfassung ans Dateiende
            java.io.RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val start = (len - 64_000).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                scan(String(buf).lineSequence())
            }
        }
        return Info(time, filament, layer, slicer)
    }

    fun formatSeconds(seconds: Long?): String? {
        if (seconds == null || seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}min" else "${m}min"
    }
}
