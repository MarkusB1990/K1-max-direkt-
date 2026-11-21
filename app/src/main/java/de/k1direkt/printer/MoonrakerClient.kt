package de.k1direkt.printer

import de.k1direkt.data.Http
import de.k1direkt.data.getText
import de.k1direkt.data.hostOnly
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Moonraker-API (Klipper). Auf dem K1 Max verfügbar nach dem Aktivieren von Root
 * (Einstellungen am Drucker) + Creality-Helper-Script, oder mit Custom-Firmware.
 * Standard-Port 7125, Fluidd läuft dann meist auf 4408, Mainsail auf 4409.
 */
class MoonrakerClient(host: String, port: Int, private val apiKey: String) : PrinterClient {

    private val base: HttpUrl = "http://${hostOnly(host)}:$port".toHttpUrl()
    override val supportsFileList = true
    override val supportsGcode = true

    private fun url(path: String, vararg query: Pair<String, String>): HttpUrl =
        base.newBuilder().apply {
            path.trim('/').split('/').forEach { addPathSegment(it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private fun request(url: HttpUrl) = Request.Builder().url(url).apply {
        if (apiKey.isNotBlank()) header("X-Api-Key", apiKey)
    }

    private suspend fun get(url: HttpUrl): JSONObject = JSONObject(Http.client.getText(request(url).get().build()))
    private suspend fun post(url: HttpUrl): JSONObject =
        JSONObject(Http.client.getText(request(url).post(ByteArray(0).toRequestBody()).build()))

    override suspend fun status(): PrinterStatus {
        // Rohe Query, weil Objektnamen wie "temperature_sensor chamber_temp" Leerzeichen enthalten
        val q = "print_stats&extruder&heater_bed&display_status&virtual_sdcard&temperature_sensor%20chamber_temp"
        val req = request("${base.toString().trimEnd('/')}/printer/objects/query?$q".toHttpUrl()).get().build()
        val st = JSONObject(Http.client.getText(req)).getJSONObject("result").getJSONObject("status")
        val ps = st.optJSONObject("print_stats") ?: JSONObject()
        val ext = st.optJSONObject("extruder")
        val bed = st.optJSONObject("heater_bed")
        val chamber = st.optJSONObject("temperature_sensor chamber_temp")
        val progress = (st.optJSONObject("display_status")?.optDouble("progress", 0.0)?.takeIf { it > 0 }
            ?: st.optJSONObject("virtual_sdcard")?.optDouble("progress", 0.0) ?: 0.0).toFloat()
        val state = when (ps.optString("state")) {
            "printing" -> PrintState.PRINTING
            "paused" -> PrintState.PAUSED
            "complete" -> PrintState.COMPLETE
            "cancelled" -> PrintState.CANCELLED
            "error" -> PrintState.ERROR
            else -> PrintState.STANDBY
        }
        val elapsed = ps.optDouble("print_duration", 0.0)
        val remaining = if (progress > 0.01f && state == PrintState.PRINTING) (elapsed / progress - elapsed).toLong() else null
        return PrinterStatus(
            state = state,
            fileName = ps.optString("filename").ifBlank { null },
            progress = progress,
            nozzleTemp = ext?.optDouble("temperature"),
            nozzleTarget = ext?.optDouble("target"),
            bedTemp = bed?.optDouble("temperature"),
            bedTarget = bed?.optDouble("target"),
            chamberTemp = chamber?.optDouble("temperature")?.takeIf { !it.isNaN() },
            elapsedSeconds = elapsed.toLong(),
            remainingSeconds = remaining,
            message = ps.optString("message").ifBlank { null },
        )
    }

    override suspend fun upload(file: File, remoteName: String, startPrint: Boolean, onProgress: (Float) -> Unit) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("root", "gcodes")
            .addFormDataPart("print", if (startPrint) "true" else "false")
            .addFormDataPart("file", remoteName, ProgressFileBody(file, "application/octet-stream".toMediaType(), onProgress))
            .build()
        Http.longClient.getText(request(url("server/files/upload")).post(body).build())
    }

    override suspend fun startPrint(remoteName: String) {
        post(url("printer/print/start", "filename" to remoteName))
    }

    override suspend fun pause() { post(url("printer/print/pause")) }
    override suspend fun resume() { post(url("printer/print/resume")) }
    override suspend fun cancel() { post(url("printer/print/cancel")) }
    override suspend fun emergencyStop() { post(url("printer/emergency_stop")) }

    override suspend fun sendGcode(script: String) {
        post(url("printer/gcode/script", "script" to script))
    }

    override suspend fun listFiles(): List<RemoteFile> {
        val arr = get(url("server/files/list", "root" to "gcodes")).optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                RemoteFile(
                    path = it.optString("path", it.optString("filename")),
                    sizeBytes = it.optLong("size"),
                    modified = (it.optDouble("modified", 0.0) * 1000).toLong(),
                )
            }
        }.sortedByDescending { it.modified }
    }

    override suspend fun deleteFile(remoteName: String) {
        val u = base.newBuilder().addPathSegments("server/files/gcodes").apply {
            remoteName.split('/').forEach { addPathSegment(it) }
        }.build()
        Http.client.getText(request(u).delete().build())
    }
}
