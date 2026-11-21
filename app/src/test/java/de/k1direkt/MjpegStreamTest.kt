package de.k1direkt

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.k1direkt.printer.MjpegStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MjpegStreamTest {

    private fun jpeg(color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    @Test
    fun liestBilderAusMjpegStream() = runBlocking {
        val server = MockWebServer()
        val body = Buffer()
        repeat(3) { i ->
            val img = jpeg(if (i % 2 == 0) Color.RED else Color.BLUE)
            body.writeUtf8("--boundarydonotcross\r\nContent-Type: image/jpeg\r\nContent-Length: ${img.size}\r\n\r\n")
            body.write(img)
            body.writeUtf8("\r\n")
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "multipart/x-mixed-replace;boundary=boundarydonotcross")
                .setBody(body)
        )
        server.start()
        val url = server.url("/?action=stream").toString()
        val connected = CompletableDeferred<String>()
        var frames = 0
        var width = 0
        val job = async(Dispatchers.IO) {
            MjpegStream.run(
                listOf(url),
                onTrying = {},
                onConnected = { connected.complete(it) },
                onFrame = { frames++; width = it.width },
            )
        }
        assertEquals(url, withTimeout(10_000) { connected.await() })
        job.await() // Stream endet nach 3 Bildern
        assertTrue("mindestens ein Bild erwartet", frames >= 1)
        assertEquals(64, width)
        server.shutdown()
    }

    @Test
    fun meldetFehlerProAdresse() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Fluidd</html>"))
        server.start()
        val urls = listOf(server.url("/a").toString(), server.url("/b").toString(), "http://127.0.0.1:1/?action=stream")
        val result = MjpegStream.run(urls, {}, {}, {})
        server.shutdown()
        assertEquals(3, result.size)
        assertTrue(result[0].error, result[0].error.contains("404"))
        assertTrue(result[1].error, result[1].error.contains("kein Kamerabild"))
        assertTrue(result[2].error, result[2].error.isNotBlank())
    }
}
