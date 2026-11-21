package de.k1direkt

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.k1direkt.data.AppSettings
import de.k1direkt.printer.PrintState
import de.k1direkt.printer.PrinterStatus
import de.k1direkt.printer.RemoteFile
import de.k1direkt.slicer.Material
import de.k1direkt.slicer.SliceParams
import de.k1direkt.slicer.SliceResult
import de.k1direkt.slicer.SupportMode
import de.k1direkt.sources.ModelSite
import de.k1direkt.sources.ProviderOutcome
import de.k1direkt.sources.SearchResult
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.SearchState
import de.k1direkt.ui.SliceUiState
import de.k1direkt.viewer.PreviewConfig
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Erzeugt Vorschaubilder der App mit Beispieldaten (ohne echten Drucker / Server).
 * Ausführen: ./gradlew recordRoborazziDebug  → Bilder in app/screenshots/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-night-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var vm: AppViewModel
    private val app get() = compose.activity.application as K1Application

    @Before
    fun setUp() {
        PreviewConfig.useOpenGl = false
        vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        vm.liveConnection = false
        runBlocking {
            app.settings.save(
                AppSettings(
                    printerHost = "192.168.178.50",
                    slicerUrl = "http://192.168.178.10:8090",
                    thingiverseToken = "demo",
                    defaultSlice = SliceParams(layerHeight = 0.2f, infillPercent = 15, material = Material.PLA, support = SupportMode.BUILDPLATE),
                )
            )
            val lib = app.library
            lib.importStream("Kabelhalter_PETG_0.20mm.gcode", gcode(), "Gesliced", null)
            lib.importStream("Schreibtisch-Organizer.3mf", ByteArrayInputStream(ByteArray(2048)), "MakerWorld", "https://makerworld.com/")
            lib.importStream("Wandhalter_Kopfhörer.stl", ByteArrayInputStream(vaseStl(segments = 6, height = 40f)), "Eigene Datei", null)
            lib.importStream("Benchy.stl", ByteArrayInputStream(vaseStl(segments = 24, height = 48f)), "Thingiverse", "https://www.thingiverse.com/thing:763622")
            lib.importStream("Vase_gedreht.stl", ByteArrayInputStream(vaseStl()), "Printables", "https://www.printables.com/model/1")
        }
        compose.waitUntil(5_000) { vm.settings.value.printerHost.isNotEmpty() && app.library.files.value.size == 5 }
        compose.waitForIdle()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun entdecken() {
        val results = listOf(
            r(ModelSite.PRINTABLES, "Kabelhalter für Schreibtisch, modular", "MakerMarta", 1840, 22100),
            r(ModelSite.THINGIVERSE, "Cable Clip – Kabelclip einfach", "jdoe3d", 512, 9800),
            r(ModelSite.PRINTABLES, "Kabelkanal mit Deckel (Print-in-Place)", "Prusa Research", 4211, 61000),
            r(ModelSite.THINGIVERSE, "Kabelhalter magnetisch", "tinkerbot", 233, 4100),
            r(ModelSite.PRINTABLES, "Ladekabel-Organizer 5-fach", "desk.design", 967, 12800),
            r(ModelSite.THINGIVERSE, "Kabelbinder wiederverwendbar", "flexi", 140, 2500),
        )
        vm.showDemoData(
            search = SearchState(
                query = "Kabelhalter",
                outcomes = listOf(
                    ProviderOutcome(ModelSite.PRINTABLES, results.filter { it.site == ModelSite.PRINTABLES }),
                    ProviderOutcome(ModelSite.THINGIVERSE, results.filter { it.site == ModelSite.THINGIVERSE }),
                    ProviderOutcome(ModelSite.CULTS3D, skipped = "Benutzer + API-Key in den Einstellungen eintragen"),
                    ProviderOutcome(ModelSite.MYMINIFACTORY, skipped = "API-Key in den Einstellungen eintragen"),
                ),
            )
        )
        shot("01_entdecken")
    }

    @Test
    fun bibliothek() {
        compose.onNodeWithText("Bibliothek").performClick()
        shot("02_bibliothek")
    }

    @Test
    fun vorschau() {
        compose.onNodeWithText("Bibliothek").performClick()
        compose.onNodeWithText("Vase_gedreht.stl").performClick()
        waitForText("Dreiecke")
        shot("03_3d_vorschau")
    }

    @Test
    fun slicen() {
        compose.onNodeWithText("Bibliothek").performClick()
        compose.onAllNodesWithContentDescription("Slicen")[0].performClick()
        waitForText("Größe:")
        shot("04_slicen")
    }

    @Test
    fun sliceFertig() {
        compose.onNodeWithText("Bibliothek").performClick()
        compose.onAllNodesWithContentDescription("Slicen")[0].performClick()
        compose.waitForIdle()
        val gcode = app.library.files.value.first { it.name.endsWith(".gcode") }.file
        waitForText("Größe:")
        vm.showDemoData(slice = SliceUiState.Done(SliceResult(gcode, "2h 14m 37s", 18.42, 54.9)))
        shot("05_slicen_fertig")
    }

    @Test
    fun drucker() {
        vm.showDemoData(
            status = PrinterStatus(
                state = PrintState.PRINTING,
                fileName = "Vase_gedreht_PLA_0.20mm.gcode",
                progress = 0.62f,
                nozzleTemp = 219.6, nozzleTarget = 220.0,
                bedTemp = 60.1, bedTarget = 60.0,
                chamberTemp = 38.0,
                elapsedSeconds = 4980, remainingSeconds = 3060,
            ),
            files = listOf(
                RemoteFile("Vase_gedreht_PLA_0.20mm.gcode", 6_400_000, System.currentTimeMillis() - 3_600_000),
                RemoteFile("Kabelhalter_PETG_0.20mm.gcode", 1_200_000, System.currentTimeMillis() - 86_400_000),
                RemoteFile("Benchy_PLA_0.16mm.gcode", 3_100_000, System.currentTimeMillis() - 3 * 86_400_000),
            ),
        )
        compose.onNodeWithText("Drucker").performClick()
        shot("06_drucker")
    }

    @Test
    fun einstellungen() {
        compose.onNodeWithText("Einstellungen").performClick()
        shot("07_einstellungen")
    }

    // ------------------------------------------------------------ Beispieldaten

    private fun r(site: ModelSite, title: String, author: String, likes: Int, downloads: Int) =
        SearchResult(site, title.hashCode().toString(), title, author, null, site.homeUrl + "#" + title.hashCode(), likes, downloads)

    private fun gcode() = ByteArrayInputStream(
        "; generated by PrusaSlicer 2.7.2\n; estimated printing time (normal mode) = 1h 3m 12s\nSTART_PRINT\nG1 X10 Y10\nEND_PRINT\n"
            .repeat(2000).toByteArray()
    )

    /** Gedrehte, gewellte Vase als binäre STL. */
    private fun vaseStl(segments: Int = 72, rings: Int = 60, height: Float = 120f): ByteArray {
        fun point(i: Int, j: Int): FloatArray {
            val z = height * j / rings
            val t = 2 * PI * i / segments + z / height * 1.4
            val base = 28f + 10f * sin(z / height * PI * 1.2).toFloat()
            val r = base * (1f + 0.08f * cos(6 * (2 * PI * i / segments)).toFloat())
            return floatArrayOf((r * cos(t)).toFloat(), (r * sin(t)).toFloat(), z)
        }
        val tris = ArrayList<Array<FloatArray>>()
        for (j in 0 until rings) for (i in 0 until segments) {
            val a = point(i, j); val b = point((i + 1) % segments, j)
            val c = point((i + 1) % segments, j + 1); val d = point(i, j + 1)
            tris += arrayOf(a, b, c); tris += arrayOf(a, c, d)
        }
        val center = floatArrayOf(0f, 0f, 0f)
        for (i in 0 until segments) tris += arrayOf(center, point((i + 1) % segments, 0), point(i, 0))
        val out = ByteArrayOutputStream()
        out.write(ByteArray(80))
        val buf = ByteBuffer.allocate(4 + tris.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(tris.size)
        for (t in tris) {
            repeat(3) { buf.putFloat(0f) }
            for (v in t) { buf.putFloat(v[0]); buf.putFloat(v[1]); buf.putFloat(v[2]) }
            buf.putShort(0)
        }
        out.write(buf.array())
        return out.toByteArray()
    }
}
