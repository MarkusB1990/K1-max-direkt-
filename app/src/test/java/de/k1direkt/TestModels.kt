package de.k1direkt

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Erzeugt Testmodelle (STL, Bambu/Orca-artiges 3MF) für Unit- und Screenshot-Tests. */
object TestModels {

    /** Gedrehte, gewellte Vase als Dreiecksliste (je Dreieck 9 Floats). */
    fun vaseTriangles(segments: Int = 72, rings: Int = 60, height: Float = 120f): List<FloatArray> {
        fun point(i: Int, j: Int): FloatArray {
            val z = height * j / rings
            val t = 2 * PI * i / segments + z / height * 1.4
            val base = 28f + 10f * sin(z / height * PI * 1.2).toFloat()
            val r = base * (1f + 0.08f * cos(6 * (2 * PI * i / segments)).toFloat())
            return floatArrayOf((r * cos(t)).toFloat(), (r * sin(t)).toFloat(), z)
        }
        val tris = ArrayList<FloatArray>()
        for (j in 0 until rings) for (i in 0 until segments) {
            val a = point(i, j); val b = point((i + 1) % segments, j)
            val c = point((i + 1) % segments, j + 1); val d = point(i, j + 1)
            tris += a + b + c; tris += a + c + d
        }
        val center = floatArrayOf(0f, 0f, 0f)
        for (i in 0 until segments) tris += center + point((i + 1) % segments, 0) + point(i, 0)
        return tris
    }

    /** Quader sx × sy × sz ab dem Ursprung. */
    fun boxTriangles(sx: Float, sy: Float, sz: Float): List<FloatArray> {
        val v = listOf(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(sx, 0f, 0f), floatArrayOf(sx, sy, 0f), floatArrayOf(0f, sy, 0f),
            floatArrayOf(0f, 0f, sz), floatArrayOf(sx, 0f, sz), floatArrayOf(sx, sy, sz), floatArrayOf(0f, sy, sz),
        )
        val f = listOf(0 to 2 to 1, 0 to 3 to 2, 4 to 5 to 6, 4 to 6 to 7, 0 to 1 to 5, 0 to 5 to 4,
            1 to 2 to 6, 1 to 6 to 5, 2 to 3 to 7, 2 to 7 to 6, 3 to 0 to 4, 3 to 4 to 7)
        return f.map { (ab, c) -> v[ab.first] + v[ab.second] + v[c] }
    }

    fun stl(tris: List<FloatArray>): ByteArray {
        val buf = ByteBuffer.allocate(84 + tris.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(ByteArray(80)); buf.putInt(tris.size)
        for (t in tris) {
            repeat(3) { buf.putFloat(0f) }
            for (x in t) buf.putFloat(x)
            buf.putShort(0)
        }
        return buf.array()
    }

    /** Mesh-XML aus Dreiecken (ohne Vertex-Deduplizierung). */
    private fun meshXml(tris: List<FloatArray>): String {
        val sb = StringBuilder("<mesh><vertices>")
        for (t in tris) for (k in 0..2) sb.append(String.format(Locale.ROOT, "<vertex x=\"%.4f\" y=\"%.4f\" z=\"%.4f\"/>", t[k * 3], t[k * 3 + 1], t[k * 3 + 2]))
        sb.append("</vertices><triangles>")
        for (i in tris.indices) sb.append("<triangle v1=\"${i * 3}\" v2=\"${i * 3 + 1}\" v3=\"${i * 3 + 2}\"/>")
        sb.append("</triangles></mesh>")
        return sb.toString()
    }

    /**
     * Projekt wie von MakerWorld/Bambu Studio:
     * - Platte 1: "Vase" (Mesh in 3D/Objects/object_1.model) + "Deckel" (Quader 40×20×10, um 90° um X gedreht)
     * - Platte 2: "Halter" (Quader 30×30×15, direkt im Haupt-Modell)
     */
    fun bambuProject(plateThumbs: List<ByteArray?> = listOf(null, null)): ByteArray {
        val ns = "xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\" " +
            "xmlns:p=\"http://schemas.microsoft.com/3dmanufacturing/production/2015/06\""
        val objectsFile = """<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" $ns>
 <resources>
  <object id="1" type="model">${meshXml(vaseTriangles(36, 30))}</object>
  <object id="2" type="model">${meshXml(boxTriangles(40f, 20f, 10f))}</object>
 </resources>
 <build/>
</model>"""
        // Rotation 90° um X: (x, y, z) -> (x, -z, y) in Zeilenvektor-Konvention
        val rotX = "1 0 0 0 0 1 0 -1 0 0 0 0"
        val root = """<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" $ns>
 <metadata name="Application">BambuStudio-02.00.00.00</metadata>
 <resources>
  <object id="3" type="model"><components><component p:path="/3D/Objects/object_1.model" objectid="1" transform="1 0 0 0 1 0 0 0 1 0 0 0"/></components></object>
  <object id="4" type="model"><components><component p:path="/3D/Objects/object_1.model" objectid="2" transform="$rotX"/></components></object>
  <object id="5" type="model" name="Halter-intern">${meshXml(boxTriangles(30f, 30f, 15f))}</object>
 </resources>
 <build>
  <item objectid="3" transform="1 0 0 0 1 0 0 0 1 128 128 0" printable="1"/>
  <item objectid="4" transform="1 0 0 0 1 0 0 0 1 60 60 0" printable="1"/>
  <item objectid="5" transform="1 0 0 0 1 0 0 0 1 400 128 0" printable="1"/>
 </build>
</model>"""
        val settings = """<?xml version="1.0" encoding="UTF-8"?>
<config>
  <object id="3"><metadata key="name" value="Vase"/><part id="1" subtype="normal_part"><metadata key="name" value="Vase-Teil"/></part></object>
  <object id="4"><metadata key="name" value="Deckel"/></object>
  <object id="5"><metadata key="name" value="Halter"/></object>
  <plate>
    <metadata key="plater_id" value="1"/>
    <metadata key="plater_name" value="Vase mit Deckel"/>
    <metadata key="thumbnail_file" value="Metadata/plate_1.png"/>
    <model_instance><metadata key="object_id" value="3"/><metadata key="instance_id" value="0"/></model_instance>
    <model_instance><metadata key="object_id" value="4"/><metadata key="instance_id" value="0"/></model_instance>
  </plate>
  <plate>
    <metadata key="plater_id" value="2"/>
    <metadata key="plater_name" value=""/>
    <metadata key="thumbnail_file" value="Metadata/plate_2.png"/>
    <model_instance><metadata key="object_id" value="5"/><metadata key="instance_id" value="0"/></model_instance>
  </plate>
</config>"""
        val rels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Target="/3D/3dmodel.model" Id="rel-1" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            put("[Content_Types].xml", "<Types/>".toByteArray())
            put("_rels/.rels", rels.toByteArray())
            put("3D/3dmodel.model", root.toByteArray())
            put("3D/Objects/object_1.model", objectsFile.toByteArray())
            put("Metadata/model_settings.config", settings.toByteArray())
            plateThumbs.forEachIndexed { i, b -> if (b != null) put("Metadata/plate_${i + 1}.png", b) }
        }
        return out.toByteArray()
    }
}
