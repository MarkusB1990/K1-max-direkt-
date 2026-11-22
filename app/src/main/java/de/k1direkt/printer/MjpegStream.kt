package de.k1direkt.printer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.k1direkt.data.Http
import de.k1direkt.data.readable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * Sucht JPEG-Anfang (FF D8) und -Ende (FF D9) im Datenstrom – unabhängig von den Multipart-Headern.
     * Lesen und Dekodieren laufen getrennt: Angezeigt wird immer nur das neueste vollständige Bild,
     * dadurch kann sich keine Verzögerung aufstauen, wenn das Handy einmal langsamer dekodiert.
     */
    private suspend fun readFrames(input: InputStream, url: String, onConnected: (String) -> Unit, onFrame: (Bitmap) -> Unit) =
        coroutineScope {
            val latest = AtomicReference<ByteArray?>(null)
            val connected = AtomicBoolean(false)
            fun show(bytes: ByteArray) {
                decode(bytes)?.let {
                    if (connected.compareAndSet(false, true)) onConnected(url)
                    onFrame(it)
                }
            }
            val decoder = launch(Dispatchers.Default) {
                while (isActive) {
                    val bytes = latest.getAndSet(null)
                    if (bytes == null) delay(10) else show(bytes)
                }
            }
            try {
                val buf = ByteArray(64 * 1024)
                val frame = ByteArrayOutputStream(256 * 1024)
                var inFrame = false
                var prev = -1
                var bytesWithoutFrame = 0L
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    var start = 0
                    for (i in 0 until n) {
                        val b = buf[i].toInt() and 0xFF
                        if (!inFrame) {
                            if (prev == 0xFF && b == 0xD8) {
                                inFrame = true
                                frame.reset()
                                frame.write(0xFF); frame.write(0xD8)
                                start = i + 1
                            }
                        } else if (prev == 0xFF && b == 0xD9) {
                            frame.write(buf, start, i + 1 - start)
                            latest.set(frame.toByteArray()) // älteres, noch nicht angezeigtes Bild wird verworfen
                            inFrame = false
                            bytesWithoutFrame = 0
                        }
                        prev = b
                    }
                    if (inFrame) {
                        frame.write(buf, start, n - start)
                        if (frame.size() > 8_000_000) inFrame = false // defektes Bild verwerfen
                    } else {
                        bytesWithoutFrame += n
                        if (bytesWithoutFrame > 2_000_000) error("kein JPEG im Stream")
                    }
                }
                // Stream zu Ende: letztes Bild noch anzeigen
                latest.getAndSet(null)?.let { show(it) }
            } finally {
                decoder.cancel()
            }
        }

    /** Dekodiert ein JPEG; sehr große Kamerabilder werden für die Anzeige verkleinert. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
