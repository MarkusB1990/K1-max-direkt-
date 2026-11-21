package de.k1direkt.viewer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.sqrt

/** Ein platziertes Teil im 3MF (ein Build-Item). */
data class TmfObject(
    val itemIndex: Int,
    val objectId: Int,
    val name: String,
    val triangles: Int,
    val sizeX: Float,
    val sizeY: Float,
    val sizeZ: Float,
)

/** Eine Druckplatte aus einem MakerWorld/Bambu/Orca-Projekt. */
data class TmfPlate(
    val number: Int,
    val name: String,
    val itemIndices: List<Int>,
    /** Pfad des Vorschaubilds im ZIP, z. B. "Metadata/plate_1.png" */
    val thumbnail: String?,
)

/**
 * Liest 3MF-Dateien inklusive der Erweiterungen von Bambu Studio/OrcaSlicer/PrusaSlicer:
 * Objekte mit Komponenten in Unterdateien (3D/Objects/…), Transformationen, Teilenamen
 * (Metadata/model_settings.config) und Platten mit Vorschaubildern.
 */
class ThreeMfProject internal constructor(
    val objects: List<TmfObject>,
    val plates: List<TmfPlate>,
    private val items: List<BuildItem>,
    private val geometry: Map<String, Map<Int, ObjDef>>,
) {
    val totalTriangles: Int get() = objects.sumOf { it.triangles }

    /** Dreiecksnetz der gewählten Teile in ihrer Original-Anordnung (für Vorschau). */
    fun mesh(itemIndices: Collection<Int> = items.indices.toList(), maxTriangles: Int = Int.MAX_VALUE): Mesh {
        val total = itemIndices.sumOf { objects.getOrNull(it)?.triangles ?: 0 }
        val step = if (total > maxTriangles) (total + maxTriangles - 1) / maxTriangles else 1
        val out = FloatList()
        var tri = 0
        var corner = 0
        val buf = FloatArray(9)
        val sink = VertexSink { x, y, z ->
            buf[corner * 3] = x; buf[corner * 3 + 1] = y; buf[corner * 3 + 2] = z
            corner++
            if (corner == 3) {
                if (tri % step == 0) for (v in buf) out.add(v)
                tri++
                corner = 0
            }
        }
        for (i in itemIndices) items.getOrNull(i)?.let { emit(it.path, it.objectId, it.transform, sink, 0) }
        val positions = out.toArray()
        if (positions.isEmpty()) throw IOException("Die gewählten Teile enthalten keine Dreiecke")
        return Mesh(positions, MeshMath.normals(positions))
    }

    /** Schreibt die gewählten Teile als binäre STL (Anordnung bleibt erhalten). */
    fun writeStl(itemIndices: Collection<Int>, target: File) {
        val mesh = mesh(itemIndices)
        val p = mesh.positions
        val count = p.size / 9
        DataOutputStream(target.outputStream().buffered(1 shl 16)).use { out ->
            out.write(ByteArray(80).also { "K1 Max Direkt".toByteArray().copyInto(it) })
            val bb = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(count).array())
            for (t in 0 until count) {
                bb.clear()
                val i = t * 9
                bb.putFloat(mesh.normals[i]); bb.putFloat(mesh.normals[i + 1]); bb.putFloat(mesh.normals[i + 2])
                for (k in 0 until 9) bb.putFloat(p[i + k])
                bb.putShort(0)
                out.write(bb.array())
            }
        }
    }

    private fun emit(path: String, id: Int, m: FloatArray, sink: VertexSink, depth: Int) {
        if (depth > 16) return
        val def = geometry[path]?.get(id) ?: return
        val v = def.verts
        val t = def.tris
        if (v != null && t != null) {
            var k = 0
            while (k + 2 < t.size) {
                for (c in 0..2) {
                    val vi = t[k + c] * 3
                    if (vi + 2 < v.size) {
                        val x = v[vi]; val y = v[vi + 1]; val z = v[vi + 2]
                        sink.vertex(
                            x * m[0] + y * m[3] + z * m[6] + m[9],
                            x * m[1] + y * m[4] + z * m[7] + m[10],
                            x * m[2] + y * m[5] + z * m[8] + m[11],
                        )
                    } else sink.vertex(0f, 0f, 0f)
                }
                k += 3
            }
        }
        for (c in def.components) {
            emit(c.path?.let { normPath(it) } ?: path, c.objectId, Transform.compose(c.transform, m), sink, depth + 1)
        }
    }

    internal fun triangleCountAndBox(item: BuildItem): Pair<Int, FloatArray> {
        val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        var verts = 0
        emit(item.path, item.objectId, item.transform, VertexSink { x, y, z ->
            verts++
            if (x < box[0]) box[0] = x; if (y < box[1]) box[1] = y; if (z < box[2]) box[2] = z
            if (x > box[3]) box[3] = x; if (y > box[4]) box[4] = y; if (z > box[5]) box[5] = z
        }, 0)
        return verts / 3 to box
    }

    companion object {
        fun read(file: File): ThreeMfProject = ZipFile(file).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            val byLower = entries.associateBy { normPath(it.name).lowercase(Locale.ROOT) }

            // Alle Modell-Dateien einlesen
            val geometry = HashMap<String, Map<Int, ObjDef>>()
            var rootItems: List<BuildItem> = emptyList()
            var rootNames: Map<Int, String> = emptyMap()
            val rootPath = rootModelPath(zip, byLower)
            for (e in entries.filter { it.name.lowercase(Locale.ROOT).endsWith(".model") }) {
                val path = normPath(e.name)
                val parsed = zip.getInputStream(e).use { parseModel(it, path) }
                geometry[path] = parsed.objects
                if (path.equals(rootPath, ignoreCase = true) || (rootItems.isEmpty() && parsed.items.isNotEmpty())) {
                    if (parsed.items.isNotEmpty()) {
                        rootItems = parsed.items
                        rootNames = parsed.objects.mapNotNull { (id, d) -> d.name?.let { id to it } }.toMap()
                    }
                }
            }
            if (rootItems.isEmpty()) throw IOException("Keine druckbaren Teile im 3MF gefunden")

            // Zusatzinfos aus Bambu/Orca-Projekten
            val settings = byLower["metadata/model_settings.config"]?.let { e -> zip.getInputStream(e).use { parseModelSettings(it) } }
            val names = rootNames + (settings?.objectNames ?: emptyMap())

            val tmp = ThreeMfProject(emptyList(), emptyList(), rootItems, geometry)
            val objects = rootItems.mapIndexed { idx, item ->
                val (tris, box) = tmp.triangleCountAndBox(item)
                TmfObject(
                    itemIndex = idx,
                    objectId = item.objectId,
                    name = names[item.objectId]?.takeIf { it.isNotBlank() } ?: "Teil ${idx + 1}",
                    triangles = tris,
                    sizeX = if (tris > 0) box[3] - box[0] else 0f,
                    sizeY = if (tris > 0) box[4] - box[1] else 0f,
                    sizeZ = if (tris > 0) box[5] - box[2] else 0f,
                )
            }.filter { it.triangles > 0 }

            val validIdx = objects.map { it.itemIndex }.toSet()
            var plates = settings?.plates.orEmpty().mapNotNull { p ->
                // model_instance verweist per object_id (+ instance_id) auf Build-Items
                val idx = p.instances.mapNotNull { (objId, inst) ->
                    rootItems.withIndex().filter { it.value.objectId == objId }.getOrNull(inst)?.index
                }.filter { it in validIdx }.distinct()
                if (idx.isEmpty()) null
                else TmfPlate(
                    number = p.number,
                    name = p.name?.takeIf { it.isNotBlank() } ?: "Platte ${p.number}",
                    itemIndices = idx,
                    thumbnail = p.thumbnail?.let { normPath(it) }?.takeIf { byLower.containsKey(it.lowercase(Locale.ROOT)) }
                        ?: "Metadata/plate_${p.number}.png".takeIf { byLower.containsKey(it.lowercase(Locale.ROOT)) },
                )
            }.sortedBy { it.number }
            if (plates.isEmpty()) {
                val thumb = listOf("Metadata/plate_1.png", "Metadata/thumbnail.png", "Metadata/top_1.png")
                    .firstOrNull { byLower.containsKey(it.lowercase(Locale.ROOT)) }
                plates = listOf(TmfPlate(1, "Alle Teile", objects.map { it.itemIndex }, thumb))
            }
            ThreeMfProject(objects, plates, rootItems, geometry)
        }

        /** Liest ein Bild (z. B. Plattenvorschau) aus dem 3MF. */
        fun readEntry(file: File, path: String): ByteArray? = runCatching {
            ZipFile(file).use { zip ->
                val e = zip.entries().toList().firstOrNull { normPath(it.name).equals(normPath(path), ignoreCase = true) }
                e?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
            }
        }.getOrNull()

        private fun rootModelPath(zip: ZipFile, byLower: Map<String, java.util.zip.ZipEntry>): String {
            val rels = byLower["_rels/.rels"] ?: return "3D/3dmodel.model"
            val text = zip.getInputStream(rels).use { it.readBytes().toString(Charsets.UTF_8) }
            val m = Regex("""Target="([^"]+\.model)"""", RegexOption.IGNORE_CASE).find(text)
            return m?.groupValues?.get(1)?.let { normPath(it) } ?: "3D/3dmodel.model"
        }

        internal fun normPath(p: String) = p.replace('\\', '/').trimStart('/')

        private class ParsedModel(val objects: Map<Int, ObjDef>, val items: List<BuildItem>)

        private fun parseModel(input: InputStream, path: String): ParsedModel {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            val objects = HashMap<Int, ObjDef>()
            val items = ArrayList<BuildItem>()
            var curId = -1
            var curName: String? = null
            var verts: FloatList? = null
            var tris: IntList? = null
            var comps = ArrayList<Comp>()
            fun attr(vararg names: String): String? {
                for (n in names) parser.getAttributeValue(null, n)?.let { return it }
                return null
            }
            while (true) {
                val ev = parser.next()
                if (ev == XmlPullParser.END_DOCUMENT) break
                val tag = parser.name?.substringAfter(':') ?: continue
                if (ev == XmlPullParser.START_TAG) {
                    when (tag) {
                        "object" -> {
                            curId = attr("id")?.toIntOrNull() ?: -1
                            curName = attr("name")
                            verts = null; tris = null; comps = ArrayList()
                        }
                        "vertices" -> verts = FloatList(1 shl 12)
                        "vertex" -> verts?.apply {
                            add(attr("x")?.toFloatOrNull() ?: 0f)
                            add(attr("y")?.toFloatOrNull() ?: 0f)
                            add(attr("z")?.toFloatOrNull() ?: 0f)
                        }
                        "triangles" -> tris = IntList(1 shl 12)
                        "triangle" -> tris?.apply {
                            add(attr("v1")?.toIntOrNull() ?: 0)
                            add(attr("v2")?.toIntOrNull() ?: 0)
                            add(attr("v3")?.toIntOrNull() ?: 0)
                        }
                        "component" -> attr("objectid")?.toIntOrNull()?.let {
                            comps += Comp(it, attr("p:path", "path"), Transform.parse(attr("transform")))
                        }
                        "item" -> attr("objectid")?.toIntOrNull()?.let {
                            if (attr("printable") != "0") {
                                items += BuildItem(it, attr("p:path", "path")?.let { p -> normPath(p) } ?: path, Transform.parse(attr("transform")))
                            }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && tag == "object" && curId >= 0) {
                    objects[curId] = ObjDef(curName, verts?.toArray(), tris?.toArray(), comps)
                    curId = -1
                }
            }
            return ParsedModel(objects, items)
        }

        private class PlateInfo(val number: Int, val name: String?, val thumbnail: String?, val instances: List<Pair<Int, Int>>)
        private class Settings(val objectNames: Map<Int, String>, val plates: List<PlateInfo>)

        private fun parseModelSettings(input: InputStream): Settings {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            val names = HashMap<Int, String>()
            val plates = ArrayList<PlateInfo>()
            val stack = ArrayList<String>()
            var objId = -1
            var plateNo = 0; var plateName: String? = null; var plateThumb: String? = null
            var instances = ArrayList<Pair<Int, Int>>()
            var instObj = -1; var instIdx = 0
            while (true) {
                val ev = parser.next()
                if (ev == XmlPullParser.END_DOCUMENT) break
                if (ev == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    val parent = stack.lastOrNull()
                    when (tag) {
                        "object" -> objId = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: -1
                        "plate" -> { plateNo = plates.size + 1; plateName = null; plateThumb = null; instances = ArrayList() }
                        "model_instance" -> { instObj = -1; instIdx = 0 }
                        "metadata" -> {
                            val key = parser.getAttributeValue(null, "key")
                            val value = parser.getAttributeValue(null, "value")
                            when (parent) {
                                "object" -> if (key == "name" && value != null && objId >= 0) names[objId] = value
                                "plate" -> when (key) {
                                    "plater_id", "index" -> value?.toIntOrNull()?.let { plateNo = it }
                                    "plater_name" -> plateName = value
                                    "thumbnail_file" -> plateThumb = value
                                }
                                "model_instance" -> when (key) {
                                    "object_id" -> instObj = value?.toIntOrNull() ?: -1
                                    "instance_id" -> instIdx = value?.toIntOrNull() ?: 0
                                }
                            }
                        }
                    }
                    stack += tag
                } else if (ev == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "model_instance" -> if (instObj >= 0) instances += instObj to instIdx
                        "plate" -> plates += PlateInfo(plateNo, plateName, plateThumb, instances)
                        "object" -> objId = -1
                    }
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                }
            }
            return Settings(names, plates)
        }
    }
}

internal class ObjDef(val name: String?, val verts: FloatArray?, val tris: IntArray?, val components: List<Comp>)
internal class Comp(val objectId: Int, val path: String?, val transform: FloatArray)
internal class BuildItem(val objectId: Int, val path: String, val transform: FloatArray)

internal fun interface VertexSink {
    fun vertex(x: Float, y: Float, z: Float)
}

/** 3MF-Transformation: 12 Werte (4×3-Matrix, Zeilenvektor-Konvention). */
internal object Transform {
    val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)

    fun parse(s: String?): FloatArray {
        if (s.isNullOrBlank()) return IDENTITY
        val v = s.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
        return if (v.size == 12) v.toFloatArray() else IDENTITY
    }

    /** Erst a, dann b anwenden: p · a · b */
    fun compose(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(12)
        for (i in 0..3) for (j in 0..2) {
            var sum = 0f
            for (k in 0..2) sum += a[i * 3 + k] * b[k * 3 + j]
            if (i == 3) sum += b[9 + j]
            r[i * 3 + j] = sum
        }
        return r
    }
}

object MeshMath {
    fun normals(p: FloatArray): FloatArray {
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

/** Wachsende Int-Liste ohne Boxing. */
class IntList(capacity: Int = 1 shl 12) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun toArray(): IntArray = data.copyOf(size)
}
