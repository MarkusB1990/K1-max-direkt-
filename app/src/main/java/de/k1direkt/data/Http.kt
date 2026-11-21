package de.k1direkt.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Für Slicen und große Uploads: deutlich längere Timeouts. */
    val longClient: OkHttpClient = client.newBuilder()
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()
}

/** Führt einen OkHttp-Call als abbrechbare Coroutine aus. */
suspend fun OkHttpClient.await(request: Request): Response = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

/**
 * Liest den Body als Text und wirft bei HTTP-Fehlern eine verständliche Exception.
 * Läuft komplett auf Dispatchers.IO – das Lesen des Bodys ist Netzwerkzugriff und
 * darf unter Android nie im UI-Thread passieren (sonst NetworkOnMainThreadException).
 */
suspend fun OkHttpClient.getText(request: Request): String = withContext(Dispatchers.IO) {
    await(request).use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw IOException("HTTP ${resp.code}: ${body.take(300).ifBlank { resp.message }}")
        }
        body
    }
}

/** Verständliche Fehlermeldung, auch für Exceptions ohne eigenen Text. */
fun Throwable.readable(): String {
    val msg = message?.takeIf { it.isNotBlank() }
    return when (this) {
        is java.net.ConnectException -> "Keine Verbindung (${msg ?: "Server aus oder falsche Adresse/Port?"})"
        is java.net.SocketTimeoutException -> "Zeitüberschreitung – Gerät antwortet nicht"
        is java.net.UnknownHostException -> "Adresse unbekannt (${msg ?: "Tippfehler?"})"
        else -> msg ?: javaClass.simpleName
    }
}
