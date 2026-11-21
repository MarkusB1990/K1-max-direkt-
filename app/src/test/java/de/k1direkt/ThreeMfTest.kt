package de.k1direkt

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.k1direkt.viewer.MeshLoader
import de.k1direkt.viewer.ThreeMfProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThreeMfTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(): Pair<File, ThreeMfProject> {
        val f = tmp.newFile("projekt.3mf")
        f.writeBytes(TestModels.bambuProject())
        return f to ThreeMfProject.read(f)
    }

    @Test
    fun liestPlattenUndTeilnamen() {
        val (_, p) = project()
        assertEquals(listOf("Vase", "Deckel", "Halter"), p.objects.map { it.name })
        assertEquals(2, p.plates.size)
        assertEquals("Vase mit Deckel", p.plates[0].name)
        assertEquals(listOf(0, 1), p.plates[0].itemIndices)
        assertEquals("Platte 2", p.plates[1].name)
        assertEquals(listOf(2), p.plates[1].itemIndices)
    }

    @Test
    fun wendetKomponentenTransformationAn() {
        val (_, p) = project()
        val deckel = p.objects[1]
        // 40 × 20 × 10 um 90° um X gedreht → 40 × 10 × 20
        assertEquals(40f, deckel.sizeX, 0.01f)
        assertEquals(10f, deckel.sizeY, 0.01f)
        assertEquals(20f, deckel.sizeZ, 0.01f)
        assertEquals(12, deckel.triangles)
        assertTrue(p.objects[0].triangles > 1000)
    }

    @Test
    fun schreibtAuswahlAlsStl() {
        val (_, p) = project()
        val out = tmp.newFile("halter.stl")
        p.writeStl(listOf(2), out)
        val bytes = out.readBytes()
        val count = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(12, count)
        assertEquals(84 + 12 * 50, bytes.size)
        val mesh = MeshLoader.load(out)
        assertEquals(30f, mesh.sizeX, 0.01f)
        assertEquals(15f, mesh.sizeZ, 0.01f)
        // Platte 1 = Vase + Deckel zusammen
        val plate = tmp.newFile("platte1.stl")
        p.writeStl(p.plates[0].itemIndices, plate)
        assertEquals(p.objects[0].triangles + 12, MeshLoader.load(plate).triangleCount)
    }

    @Test
    fun liestProjektEinstellungen() {
        val (_, p) = project()
        assertEquals(0.16f, p.settings.layerHeight!!, 0.001f)
        assertEquals(3, p.settings.wallLoops)
        assertEquals(20, p.settings.infillPercent)
        assertEquals(true, p.settings.supportEnabled)
        assertEquals(true, p.settings.supportBuildPlateOnly)
        assertEquals("PETG", p.settings.filamentType)
    }

    @Test
    fun vorschauLaedtGanzesProjekt() {
        val (f, p) = project()
        assertEquals(p.totalTriangles, MeshLoader.load(f).triangleCount)
    }
}
