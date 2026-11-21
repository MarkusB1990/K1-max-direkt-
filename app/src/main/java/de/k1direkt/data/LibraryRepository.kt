package de.k1direkt.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

enum class FileType(val label: String, val sliceable: Boolean, val printable: Boolean) {
    STL("STL", true, false),
    THREE_MF("3MF", true, false),
    OBJ("OBJ", true, false),
    STEP("STEP", true, false),
    GCODE("G-Code", false, true),
    OTHER("Datei", false, false);

    companion object {
        fun of(name: String): FileType = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "stl" -> STL
            "3mf" -> THREE_MF
            "obj" -> OBJ
            "step", "stp" -> STEP
            "gcode", "gco", "g" -> GCODE
            else -> OTHER
        }

        /** Endungen, die beim Entpacken von ZIP-Archiven übernommen werden. */
        val keepExtensions = setOf("stl", "3mf", "obj", "step", "stp", "gcode", "gco")
    }
}

data class LibraryFile(
    val file: File,
    val type: FileType,
    val source: String,
    val sourceUrl: String?,
    val addedAt: Long,
) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    val isOwn: Boolean get() = source == SOURCE_OWN
}

const val SOURCE_OWN = "Eigene Datei"
const val SOURCE_SLICED = "Gesliced"

class LibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "library").apply { mkdirs() }
    private val indexFile = File(dir, ".index.json")
    private val mutex = Mutex()

    private val _files = MutableStateFlow<List<LibraryFile>>(emptyList())
    val files: StateFlow<List<LibraryFile>> = _files.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { _files.value = readAll() }
    }

    private fun readIndex(): JSONObject =
        runCatching { JSONObject(indexFile.readText()) }.getOrElse { JSONObject() }

    private fun readAll(): List<LibraryFile> {
        val index = readIndex()
        return dir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { f ->
                val meta = index.optJSONObject(f.name)
                LibraryFile(
                    file = f,
                    type = FileType.of(f.name),
                    source = meta?.optString("source")?.ifBlank { null } ?: SOURCE_OWN,
                    sourceUrl = meta?.optString("url")?.ifBlank { null },
                    addedAt = meta?.optLong("added", f.lastModified()) ?: f.lastModified(),
                )
            }
            .sortedByDescending { it.addedAt }
    }

    private fun writeMeta(name: String, source: String, url: String?) {
        val index = readIndex()
        index.put(name, JSONObject().apply {
            put("source", source)
            put("url", url ?: "")
            put("added", System.currentTimeMillis())
        })
        indexFile.writeText(index.toString())
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimStart('.')
        return cleaned.ifBlank { "modell_${System.currentTimeMillis()}" }.take(120)
    }

    private fun uniqueFile(name: String): File {
        val safe = sanitize(name)
        var f = File(dir, safe)
        if (!f.exists()) return f
        val base = safe.substringBeforeLast('.')
        val ext = safe.substringAfterLast('.', "")
        var i = 2
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    /**
     * Speichert einen Datenstrom in der Bibliothek. ZIP-Archive werden entpackt
     * und nur 3D-/G-Code-Dateien übernommen. Gibt die neu angelegten Dateien zurück.
     */
    suspend fun importStream(
        fileName: String,
        input: InputStream,
        source: String,
        sourceUrl: String? = null,
    ): List<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val created = mutableListOf<File>()
            if (fileName.lowercase(Locale.ROOT).endsWith(".zip")) {
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val entryName = entry.name.substringAfterLast('/')
                        val ext = entryName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (!entry.isDirectory && ext in FileType.keepExtensions && !entryName.startsWith(".")) {
                            val target = uniqueFile(entryName)
                            target.outputStream().use { zip.copyTo(it) }
                            writeMeta(target.name, source, sourceUrl)
                            created += target
                        }
                    }
                }
                if (created.isEmpty()) throw IOException("Im ZIP-Archiv wurden keine STL/3MF/OBJ/G-Code-Dateien gefunden.")
            } else {
                val target = uniqueFile(fileName)
                input.use { inp -> target.outputStream().use { inp.copyTo(it) } }
                writeMeta(target.name, source, sourceUrl)
                created += target
            }
            _files.value = readAll()
            created
        }
    }

    /** Übernimmt eine vom Nutzer gewählte oder geteilte Datei (content://-Uri). */
    suspend fun importUri(uri: Uri, source: String = SOURCE_OWN): List<File> {
        val name = displayName(uri) ?: "datei_${System.currentTimeMillis()}.stl"
        val stream = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)
        } ?: throw IOException("Datei konnte nicht geöffnet werden")
        return importStream(name, stream, source)
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    /** Freie Zieldatei für einen Slice-Vorgang (wird danach mit [registerFile] eingetragen). */
    fun newFile(name: String): File = uniqueFile(name)

    suspend fun registerFile(file: File, source: String, sourceUrl: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeMeta(file.name, source, sourceUrl)
            _files.value = readAll()
        }
    }

    suspend fun delete(item: LibraryFile) = withContext(Dispatchers.IO) {
        mutex.withLock {
            item.file.delete()
            val index = readIndex()
            index.remove(item.name)
            indexFile.writeText(index.toString())
            _files.value = readAll()
        }
    }

    suspend fun rename(item: LibraryFile, newName: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            var name = sanitize(newName)
            val oldExt = item.name.substringAfterLast('.', "")
            if (oldExt.isNotEmpty() && !name.lowercase(Locale.ROOT).endsWith(".${oldExt.lowercase(Locale.ROOT)}")) {
                name = "$name.$oldExt"
            }
            val target = File(dir, name)
            if (target.exists()) return@withLock false
            if (!item.file.renameTo(target)) return@withLock false
            val index = readIndex()
            val meta = index.optJSONObject(item.name)
            index.remove(item.name)
            if (meta != null) index.put(name, meta)
            indexFile.writeText(index.toString())
            _files.value = readAll()
            true
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.GERMANY, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format(Locale.GERMANY, "%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024 -> String.format(Locale.GERMANY, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
