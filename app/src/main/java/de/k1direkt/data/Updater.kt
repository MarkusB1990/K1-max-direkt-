package de.k1direkt.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.k1direkt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * In-App-Update über GitHub-Releases: Jeder fest signierte CI-Build wird als Release mit
 * APK + version.json veröffentlicht (siehe .github/workflows/build-apk.yml).
 */
object Updater {
    const val REPO = "MarkusB1990/K1-max-direkt-"

    /** In Tests abschaltbar (keine Netzwerkzugriffe). */
    @Volatile
    var autoCheck = true

    val currentCode: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    /** Liefert ein neueres Release oder null, wenn die App aktuell ist. */
    suspend fun check(): UpdateInfo? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "K1MaxDirekt-App")
            .build()
        val release = JSONObject(Http.client.getText(req))
        val assets = release.optJSONArray("assets") ?: throw IOException("Release ohne Dateien")
        var apk: JSONObject? = null
        var versionJsonUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            when {
                name.endsWith(".apk") -> apk = a
                name == "version.json" -> versionJsonUrl = a.optString("browser_download_url")
            }
        }
        if (apk == null || versionJsonUrl == null) throw IOException("Release unvollständig")
        val version = JSONObject(Http.client.getText(Request.Builder().url(versionJsonUrl).header("User-Agent", "K1MaxDirekt-App").build()))
        val code = version.optInt("versionCode")
        if (code <= currentCode) return null
        return UpdateInfo(
            versionCode = code,
            versionName = version.optString("versionName"),
            notes = release.optString("body").trim(),
            apkUrl = apk.optString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    /** Lädt das APK in den Cache (mit Fortschritt 0…1). */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "K1MaxDirekt-${info.versionName}.apk")
        val req = Request.Builder().url(info.apkUrl).header("User-Agent", "K1MaxDirekt-App").build()
        Http.longClient.await(req).use { resp ->
            if (!resp.isSuccessful) throw IOException("Download fehlgeschlagen (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Leere Antwort")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != lastPercent && percent >= 0) {
                            lastPercent = percent
                            onProgress((percent / 100f).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        target
    }

    /** Darf die App Pakete installieren? (Android 8+: „Unbekannte Apps installieren“) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Öffnet den Android-Installer für das heruntergeladene APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
