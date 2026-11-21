package de.k1direkt.printer

import de.k1direkt.data.AppSettings
import de.k1direkt.data.PrinterMode
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

enum class PrintState(val label: String) {
    OFFLINE("Nicht verbunden"),
    STANDBY("Bereit"),
    PRINTING("Druckt"),
    PAUSED("Pausiert"),
    COMPLETE("Fertig"),
    CANCELLED("Abgebrochen"),
    ERROR("Fehler"),
}

data class PrinterStatus(
    val state: PrintState = PrintState.OFFLINE,
    val fileName: String? = null,
    /** 0.0 … 1.0 */
    val progress: Float = 0f,
    val nozzleTemp: Double? = null,
    val nozzleTarget: Double? = null,
    val bedTemp: Double? = null,
    val bedTarget: Double? = null,
    val chamberTemp: Double? = null,
    val elapsedSeconds: Long? = null,
    val remainingSeconds: Long? = null,
    val message: String? = null,
)

data class RemoteFile(val path: String, val sizeBytes: Long, val modified: Long)

interface PrinterClient {
    val supportsFileList: Boolean
    val supportsGcode: Boolean

    suspend fun status(): PrinterStatus
    suspend fun upload(file: File, remoteName: String, startPrint: Boolean, onProgress: (Float) -> Unit)
    suspend fun startPrint(remoteName: String)
    suspend fun pause()
    suspend fun resume()
    suspend fun cancel()
    suspend fun listFiles(): List<RemoteFile>
    suspend fun deleteFile(remoteName: String)
    suspend fun sendGcode(script: String)
    suspend fun emergencyStop()
    /** Von der Firmware gemeldete Kamera-Stream-URLs (falls bekannt). */
    suspend fun webcamUrls(): List<String> = emptyList()
    fun close() {}

    companion object {
        fun create(settings: AppSettings): PrinterClient = when (settings.printerMode) {
            PrinterMode.MOONRAKER -> MoonrakerClient(settings.printerHost, settings.moonrakerPort, settings.moonrakerApiKey)
            PrinterMode.CREALITY_STOCK -> CrealityStockClient(settings.printerHost)
        }
    }
}

/** RequestBody, der den Upload-Fortschritt meldet. */
class ProgressFileBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = contentType
    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length().coerceAtLeast(1)
        var written = 0L
        var lastReported = -1
        file.source().use { src ->
            val buffer = okio.Buffer()
            while (true) {
                val read = src.read(buffer, 64 * 1024)
                if (read == -1L) break
                sink.write(buffer, read)
                written += read
                val pct = (written * 100 / total).toInt()
                if (pct != lastReported) {
                    lastReported = pct
                    onProgress(written.toFloat() / total)
                }
            }
        }
    }
}
