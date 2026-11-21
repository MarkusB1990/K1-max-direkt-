package de.k1direkt.printer

import de.k1direkt.data.Http
import de.k1direkt.data.getText
import de.k1direkt.data.hostOnly
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * EXPERIMENTELL: Original-Firmware des K1 Max ohne Root.
 * Status/Steuerung laufen über den WebSocket auf Port 9999 (wie bei Creality Print).
 * Das Protokoll ist nicht offiziell dokumentiert und kann sich mit Firmware-Updates ändern.
 * Für zuverlässigen Betrieb wird Root + Moonraker empfohlen.
 */
class CrealityStockClient(host: String) : PrinterClient {

    private val host = hostOnly(host)
    override val supportsFileList = false
    override val supportsGcode = false

    private val state = ConcurrentHashMap<String, Any>()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var lastError: String? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            lastError = null
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.contains("heart_beat")) {
                webSocket.send("ok")
                return
            }
            runCatching {
                val o = JSONObject(text)
                o.keys().forEach { k -> state[k] = o.get(k) }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
            lastError = t.message
            socket = null
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            socket = null
        }
    }

    private suspend fun ensureConnected() {
        if (socket == null) {
            socket = Http.client.newWebSocket(Request.Builder().url("ws://$host:9999").build(), listener)
        }
        withTimeoutOrNull(5_000) { while (!connected) delay(100) }
            ?: throw IOException("Keine Verbindung zu ws://$host:9999 ${lastError?.let { "($it)" } ?: ""}")
    }

    private suspend fun send(params: JSONObject) {
        ensureConnected()
        val ok = socket?.send(JSONObject().put("method", "set").put("params", params).toString()) ?: false
        if (!ok) throw IOException("Befehl konnte nicht gesendet werden")
    }

    private fun num(key: String): Double? = state[key]?.toString()?.toDoubleOrNull()

    override suspend fun status(): PrinterStatus {
        ensureConnected()
        // Nach dem Verbinden schickt der Drucker die Werte von selbst – kurz warten, falls noch leer.
        if (state.isEmpty()) withTimeoutOrNull(3_000) { while (state.isEmpty()) delay(100) }
        val progress = (num("printProgress") ?: 0.0).toFloat() / 100f
        val st = when (num("state")?.toInt()) {
            1 -> PrintState.PRINTING
            2 -> PrintState.COMPLETE
            3 -> PrintState.ERROR
            4 -> PrintState.CANCELLED
            5 -> PrintState.PAUSED
            else -> if ((num("printProgress") ?: 0.0) in 0.5..99.5) PrintState.PRINTING else PrintState.STANDBY
        }
        return PrinterStatus(
            state = st,
            fileName = state["printFileName"]?.toString()?.substringAfterLast('/')?.ifBlank { null },
            progress = progress,
            nozzleTemp = num("nozzleTemp"),
            nozzleTarget = num("targetNozzleTemp"),
            bedTemp = num("bedTemp0"),
            bedTarget = num("targetBedTemp0"),
            chamberTemp = num("boxTemp"),
            elapsedSeconds = num("printJobTime")?.toLong(),
            remainingSeconds = num("printLeftTime")?.toLong(),
        )
    }

    override suspend fun upload(file: File, remoteName: String, startPrint: Boolean, onProgress: (Float) -> Unit) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", remoteName, ProgressFileBody(file, "application/octet-stream".toMediaType(), onProgress))
            .build()
        try {
            Http.longClient.getText(Request.Builder().url("http://$host/upload/$remoteName").post(body).build())
        } catch (e: IOException) {
            throw IOException(
                "Upload über die Original-Firmware fehlgeschlagen (${e.message}). " +
                    "Tipp: Root am Drucker aktivieren und Moonraker verwenden.", e
            )
        }
        if (startPrint) startPrint(remoteName)
    }

    override suspend fun startPrint(remoteName: String) =
        send(JSONObject().put("opGcodeFile", "printprt:/usr/data/printer_data/gcodes/$remoteName"))

    override suspend fun pause() = send(JSONObject().put("pause", 1))
    override suspend fun resume() = send(JSONObject().put("pause", 0))
    override suspend fun cancel() = send(JSONObject().put("stop", 1))

    override suspend fun listFiles(): List<RemoteFile> = emptyList()
    override suspend fun deleteFile(remoteName: String) {
        throw UnsupportedOperationException("Mit der Original-Firmware nicht verfügbar")
    }

    override suspend fun sendGcode(script: String) {
        throw UnsupportedOperationException("Mit der Original-Firmware nicht verfügbar")
    }

    override suspend fun emergencyStop() = cancel()

    override fun close() {
        socket?.close(1000, null)
        socket = null
    }
}
