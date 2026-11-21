package de.k1direkt.viewer

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

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

    private fun load3mf(file: File): Mesh = ThreeMfProject.read(file).mesh(maxTriangles = MAX_TRIANGLES)

    // ---------- Hilfsfunktionen ----------

    private val WS = Regex("\\s+")

    private fun computeNormals(p: FloatArray): FloatArray = MeshMath.normals(p)
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
