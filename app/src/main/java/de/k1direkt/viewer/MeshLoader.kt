package de.k1direkt.viewer

import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.sqrt

/** Dreiecksnetz als "Triangle Soup": je Dreieck 3 Eckpunkte à xyz, plus Normale je Eckpunkt. */
class Mesh(val positions: FloatArray, val normals: FloatArray) {
    val triangleCount get() = positions.size / 9
    val min = FloatArray(3) { Float.MAX_VALUE }
    val max = FloatArray(3) { -Float.MAX_VALUE }

    init {
        var i = 0
        while (i < positions.size) {
            for (a in 0..2) {
                val v = positions[i + a]
                if (v < min[a]) min[a] = v
                if (v > max[a]) max[a] = v
            }
            i += 3
        }
    }

    val sizeX get() = max[0] - min[0]
    val sizeY get() = max[1] - min[1]
    val sizeZ get() = max[2] - min[2]
}

object MeshLoader {
    /** Sehr große Modelle werden für die Vorschau ausgedünnt, damit das Handy flüssig bleibt. */
    private const val MAX_TRIANGLES = 1_500_000

    fun load(file: File): Mesh = when (file.extension.lowercase(Locale.ROOT)) {
        "stl" -> loadStl(file)
        "3mf" -> load3mf(file)
        "obj" -> file.inputStream().buffered().use { loadObj(it) }
        else -> throw IOException("Vorschau für .${file.extension} nicht verfügbar")
    }

    // ---------- STL ----------

    private fun loadStl(file: File): Mesh {
        val len = file.length()
        file.inputStream().use { input ->
            val header = ByteArray(84)
            val read = input.readNBytesCompat(header)
            if (read == 84) {
                val count = ByteBuffer.wrap(header, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                // Binär-STL erkennt man zuverlässig an der passenden Dateigröße
                if (84 + count * 50 == len) return loadBinaryStl(file, count.toInt())
            }
        }
        return file.bufferedReader().use { loadAsciiStl(it.lineSequence()) }
    }

    private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private fun loadBinaryStl(file: File, count: Int): Mesh {
        val step = if (count > MAX_TRIANGLES) (count + MAX_TRIANGLES - 1) / MAX_TRIANGLES else 1
        val keep = (count + step - 1) / step
        val pos = FloatArray(keep * 9)
        var p = 0
        BufferedInputStream(file.inputStream(), 1 shl 16).use { input ->
            input.skip(84)
            val rec = ByteArray(50)
            val bb = ByteBuffer.wrap(rec).order(ByteOrder.LITTLE_ENDIAN)
            for (t in 0 until count) {
                if (input.readNBytesCompat(rec) < 50) break
                if (t % step != 0 || p + 9 > pos.size) continue
                for (k in 0 until 9) pos[p + k] = bb.getFloat(12 + k * 4)
                p += 9
            }
        }
        val positions = if (p == pos.size) pos else pos.copyOf(p)
        return Mesh(positions, computeNormals(positions))
    }

    private fun loadAsciiStl(lines: Sequence<String>): Mesh {
        val out = FloatList()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("vertex")) {
                val parts = line.split(WS)
                if (parts.size >= 4) {
                    out.add(parts[1].toFloat()); out.add(parts[2].toFloat()); out.add(parts[3].toFloat())
                }
            }
        }
        val positions = out.toArray().let { it.copyOf(it.size - it.size % 9) }
        if (positions.isEmpty()) throw IOException("Keine Dreiecke in der STL-Datei gefunden")
        return Mesh(positions, computeNormals(positions))
    }

    // ---------- OBJ ----------

    private fun loadObj(input: InputStream): Mesh {
        val verts = FloatList()
        val out = FloatList()
        input.bufferedReader().lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("v ") -> {
                    val p = line.split(WS)
                    verts.add(p[1].toFloat()); verts.add(p[2].toFloat()); verts.add(p[3].toFloat())
                }
                line.startsWith("f ") -> {
                    val idx = line.split(WS).drop(1).map { tok ->
                        val i = tok.substringBefore('/').toInt()
                        if (i < 0) verts.size / 3 + i else i - 1
                    }
                    for (k in 1 until idx.size - 1) {
                        for (vi in intArrayOf(idx[0], idx[k], idx[k + 1])) {
                            out.add(verts[vi * 3]); out.add(verts[vi * 3 + 1]); out.add(verts[vi * 3 + 2])
                        }
                    }
                }
            }
        }
        val positions = out.toArray()
        if (positions.isEmpty()) throw IOException("Keine Flächen in der OBJ-Datei gefunden")
        return Mesh(positions, computeNormals(positions))
    }

    // ---------- 3MF ----------

    private fun load3mf(file: File): Mesh {
        val out = FloatList()
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList().filter {
                !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".model")
            }
            if (entries.isEmpty()) throw IOException("Keine 3D-Daten im 3MF gefunden")
            for (entry in entries) {
                zip.getInputStream(entry).use { parse3mfModel(it, out) }
            }
        }
        val positions = out.toArray()
        if (positions.isEmpty()) throw IOException("Das 3MF enthält keine Dreiecke")
        return Mesh(positions, computeNormals(positions))
    }

    private fun parse3mfModel(input: InputStream, out: FloatList) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val verts = FloatList()
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG) continue
            when (parser.name.substringAfter(':')) {
                "mesh" -> verts.clear()
                "vertex" -> {
                    verts.add(parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f)
                    verts.add(parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f)
                    verts.add(parser.getAttributeValue(null, "z")?.toFloatOrNull() ?: 0f)
                }
                "triangle" -> {
                    if (out.size / 9 >= MAX_TRIANGLES) continue
                    for (attr in arrayOf("v1", "v2", "v3")) {
                        val i = parser.getAttributeValue(null, attr)?.toIntOrNull() ?: 0
                        if (i * 3 + 2 < verts.size) {
                            out.add(verts[i * 3]); out.add(verts[i * 3 + 1]); out.add(verts[i * 3 + 2])
                        } else {
                            out.add(0f); out.add(0f); out.add(0f)
                        }
                    }
                }
            }
        }
    }

    // ---------- Hilfsfunktionen ----------

    private val WS = Regex("\\s+")

    private fun computeNormals(p: FloatArray): FloatArray {
        val n = FloatArray(p.size)
        var i = 0
        while (i + 8 < p.size) {
            val ux = p[i + 3] - p[i]; val uy = p[i + 4] - p[i + 1]; val uz = p[i + 5] - p[i + 2]
            val vx = p[i + 6] - p[i]; val vy = p[i + 7] - p[i + 1]; val vz = p[i + 8] - p[i + 2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l > 0f) { nx /= l; ny /= l; nz /= l }
            for (k in 0..2) {
                n[i + k * 3] = nx; n[i + k * 3 + 1] = ny; n[i + k * 3 + 2] = nz
            }
            i += 9
        }
        return n
    }
}

/** Wachsende Float-Liste ohne Boxing. */
class FloatList(capacity: Int = 1 shl 16) {
    private var data = FloatArray(capacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]
    fun clear() { size = 0 }
    fun toArray(): FloatArray = data.copyOf(size)
}
