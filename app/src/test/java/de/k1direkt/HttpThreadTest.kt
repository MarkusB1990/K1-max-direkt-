package de.k1direkt

import de.k1direkt.data.getText
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Regressionstest: Unter Android ist Netzwerk-I/O im UI-Thread verboten (NetworkOnMainThreadException).
 * getText() muss den Antwort-Body daher immer auf einem Hintergrund-Thread lesen –
 * auch wenn es aus dem UI-Thread (hier simuliert) aufgerufen wird.
 */
class HttpThreadTest {

    @Test
    fun bodyWirdNichtImAufrufendenThreadGelesen() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.start()

        val readThreads = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val resp = chain.proceed(chain.request())
                val body = resp.body!!
                val tracking = object : ForwardingSource(body.source()) {
                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                        readThreads += Thread.currentThread().name
                        return super.read(sink, byteCount)
                    }
                }.buffer()
                resp.newBuilder().body(tracking.asResponseBody(body.contentType(), body.contentLength())).build()
            }
            .build()

        val uiThread = Executors.newSingleThreadExecutor { r -> Thread(r, "ui-simuliert") }
        try {
            val text = runBlocking {
                withContext(uiThread.asCoroutineDispatcher()) {
                    client.getText(Request.Builder().url(server.url("/health")).build())
                }
            }
            assertEquals("""{"status":"ok"}""", text)
            assertTrue("Body wurde gar nicht gelesen", readThreads.isNotEmpty())
            readThreads.forEach { assertNotEquals("Body im UI-Thread gelesen!", "ui-simuliert", it) }
        } finally {
            uiThread.shutdown()
            server.shutdown()
        }
    }
}
