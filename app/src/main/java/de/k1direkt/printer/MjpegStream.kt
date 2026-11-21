package de.k1direkt.printer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.k1direkt.data.Http
import de.k1direkt.data.readable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Ergebnis eines Verbindungsversuchs – für die Fehleranzeige in der App. */
data class CameraAttempt(val url: String, val error: String)

/**
 * Liest MJPEG-Streams (mjpg-streamer der Creality-Kamera, Crowsnest, ustreamer) direkt per HTTP
 * und liefert die Einzelbilder. Unterstützt auch einzelne JPEG-Schnappschüsse (wird dann gepollt).
 */
object MjpegStream {

    private val client = Http.client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Probiert die Adressen der Reihe nach. Ruft [onConnected] mit der funktionierenden URL auf und
     * danach [onFrame] für jedes Bild, bis die Coroutine abgebrochen wird. Gibt die Fehlversuche zurück,
     * falls keine Adresse funktioniert.
     */
    suspend fun run(
        urls: List<String>,
        onTrying: (String) -> Unit,
        onConnected: (String) -> Unit,
        onFrame: (Bitmap) -> Unit,
    ): List<CameraAttempt> {
        val attempts = mutableListOf<CameraAttempt>()
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            onTrying(url)
            try {
                stream(url, onConnected, onFrame)
                // stream() kehrt nur zurück, wenn die Verbindung endet → nächster Versuch
                attempts += CameraAttempt(url, "Stream beendet")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempts += CameraAttempt(url, if (e is IllegalStateException) e.message ?: "kein Kamerabild" else e.readable())
            }
        }
        return attempts
    }

    private suspend fun stream(url: String, onConnected: (String) -> Unit, onFrame: (Bitmap) -> Unit) {
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build())
        // Beim Ausschalten der Kamera die Verbindung sofort trennen (blockierendes Lesen beenden)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        val resp = try {
            call.execute()
        } catch (e: IOException) {
            handle?.dispose()
            throw e
        }
        resp.use {
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val type = resp.header("Content-Type").orEmpty().lowercase()
            val body = resp.body ?: throw IOException("leere Antwort")
            when {
                type.startsWith("image/") -> {
                    // Einzelbild → Schnappschuss-Modus (jede Sekunde neu laden)
                    val first = BitmapFactory.decodeStream(body.byteStream()) ?: error("Bild nicht lesbar")
                    onConnected(url)
                    onFrame(first)
                    while (currentCoroutineContext().isActive) {
                        delay(1000)
                        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                            r.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }?.let(onFrame)
                        }
                    }
                }
                type.contains("multipart") || type.isEmpty() -> readFrames(body.byteStream(), url, onConnected, onFrame)
                else -> error("kein Kamerabild ($type)")
            }
        }
        handle?.dispose()
    }

    /** Sucht JPEG-Anfang (FF D8) und -Ende (FF D9) im Datenstrom – funktioniert unabhängig von den Multipart-Headern. */
    private suspend fun readFrames(input: InputStream, url: String, onConnected: (String) -> Unit, onFrame: (Bitmap) -> Unit) {
        val stream = input.buffered(64 * 1024)
        val frame = ByteArrayOutputStream(256 * 1024)
        var inFrame = false
        var prev = -1
        var connected = false
        var lastDecode = 0L
        var bytesWithoutFrame = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val b = stream.read()
            if (b < 0) return
            if (!inFrame) {
                bytesWithoutFrame++
                if (bytesWithoutFrame > 2_000_000) error("kein JPEG im Stream")
                if (prev == 0xFF && b == 0xD8) {
                    inFrame = true
                    frame.reset()
                    frame.write(0xFF); frame.write(0xD8)
                }
            } else {
                frame.write(b)
                if (prev == 0xFF && b == 0xD9) {
                    inFrame = false
                    bytesWithoutFrame = 0
                    val now = System.currentTimeMillis()
                    if (now - lastDecode >= 80) { // max. ~12 Bilder/s anzeigen
                        lastDecode = now
                        val bytes = frame.toByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                            if (!connected) { connected = true; onConnected(url) }
                            onFrame(it)
                        }
                    }
                } else if (frame.size() > 8_000_000) {
                    inFrame = false // defektes Bild verwerfen
                }
            }
            prev = b
        }
    }
}
