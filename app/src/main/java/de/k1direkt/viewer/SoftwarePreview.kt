package de.k1direkt.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Steuert, ob die 3D-Vorschau OpenGL nutzt. In Screenshot-Tests (ohne GPU) wird auf Software umgeschaltet. */
object PreviewConfig {
    @Volatile
    var useOpenGl: Boolean = true
}

/**
 * Einfache Software-Darstellung (Painter-Algorithmus) – gleiche Kamera und Farben wie die OpenGL-Vorschau.
 * Für große Modelle wird ausgedünnt, damit das Zeichnen schnell bleibt.
 */
@Composable
fun SoftwareMeshPreview(mesh: Mesh, modifier: Modifier = Modifier, yawDeg: Float = -60f, pitchDeg: Float = 25f) {
    val tris = remember(mesh, yawDeg, pitchDeg) { project(mesh, yawDeg, pitchDeg) }
    Canvas(modifier) {
        drawRect(Color(0xFF17191F))
        val sc = 0.8f * size.minDimension / tris.extent
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun pt(x: Float, y: Float) = Offset(cx + (x - tris.midX) * sc, cy - (y - tris.midY) * sc)
        // Druckplatte
        for (l in tris.grid) drawLine(Color(0xFF59616F), pt(l[0], l[1]), pt(l[2], l[3]), strokeWidth = 1.2f)
        val path = Path()
        for (t in tris.faces) {
            path.reset()
            val a = pt(t[0], t[1]); val b = pt(t[2], t[3]); val c = pt(t[4], t[5])
            path.moveTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(c.x, c.y); path.close()
            val k = t[6]
            drawPath(path, Color(0.30f * k, 0.56f * k, 1.0f * k))
        }
    }
}

private class Projected(val faces: List<FloatArray>, val grid: List<FloatArray>, val midX: Float, val midY: Float, val extent: Float)

private fun project(mesh: Mesh, yawDeg: Float, pitchDeg: Float): Projected {
    // gleiche Blickrichtung wie ModelRenderer: Kamera dreht um Z, Z zeigt nach oben
    val y = Math.toRadians(-(yawDeg.toDouble() + 90.0))
    val p = Math.toRadians(pitchDeg.toDouble())
    val cy = cos(y).toFloat(); val sy = sin(y).toFloat()
    val cp = cos(p).toFloat(); val sp = sin(p).toFloat()
    val ox = -(mesh.min[0] + mesh.max[0]) / 2f
    val oy = -(mesh.min[1] + mesh.max[1]) / 2f
    val oz = -mesh.min[2]
    // Lichtrichtungen wie im Shader (Welt-Koordinaten)
    val l1 = norm(0.4f, -0.5f, 0.75f)
    val l2 = norm(-0.6f, 0.4f, 0.3f)

    fun view(x: Float, yy: Float, z: Float): FloatArray {
        val xr = x * cy - yy * sy
        val yr = x * sy + yy * cy
        return floatArrayOf(xr, z * cp + yr * sp, yr * cp - z * sp)
    }

    val step = max(1, mesh.triangleCount / 60_000)
    val faces = ArrayList<FloatArray>(mesh.triangleCount / step)
    val pos = mesh.positions
    var t = 0
    while (t < mesh.triangleCount) {
        val i = t * 9
        val a = view(pos[i] + ox, pos[i + 1] + oy, pos[i + 2] + oz)
        val b = view(pos[i + 3] + ox, pos[i + 4] + oy, pos[i + 5] + oz)
        val c = view(pos[i + 6] + ox, pos[i + 7] + oy, pos[i + 8] + oz)
        val nx = mesh.normals[i]; val ny = mesh.normals[i + 1]; val nz = mesh.normals[i + 2]
        val key = abs(nx * l1[0] + ny * l1[1] + nz * l1[2])
        val fill = abs(nx * l2[0] + ny * l2[1] + nz * l2[2])
        val shade = (0.25f + 0.65f * key + 0.25f * fill).coerceAtMost(1f)
        faces += floatArrayOf(a[0], a[1], b[0], b[1], c[0], c[1], shade, (a[2] + b[2] + c[2]) / 3f)
        t += step
    }
    faces.sortByDescending { it[7] } // hinten zuerst zeichnen

    val grid = ArrayList<FloatArray>()
    val half = 150f
    var v = -half
    while (v <= half + 0.1f) {
        val g1 = view(v, -half, 0f); val g2 = view(v, half, 0f)
        val g3 = view(-half, v, 0f); val g4 = view(half, v, 0f)
        grid += floatArrayOf(g1[0], g1[1], g2[0], g2[1])
        grid += floatArrayOf(g3[0], g3[1], g4[0], g4[1])
        v += 25f
    }
    // Ausschnitt: Modell gut sichtbar, Platte darf angeschnitten sein
    val modelExtent = max(max(mesh.sizeX, mesh.sizeY), mesh.sizeZ).coerceAtLeast(10f)
    val midY = view(0f, 0f, mesh.sizeZ / 2f)[1]
    return Projected(faces, grid, 0f, midY, modelExtent * 1.9f)
}

private fun norm(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z)
    return floatArrayOf(x / l, y / l, z / l)
}
