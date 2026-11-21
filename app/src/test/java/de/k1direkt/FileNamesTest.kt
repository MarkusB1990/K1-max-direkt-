package de.k1direkt

import de.k1direkt.data.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {

    @Test
    fun rfc5987NameVonMakerWorld() {
        val header = "attachment; filename*=UTF-8''Victorian_Christmas_street_lamp_e27_ams%20%E2%80%93%20lower_base_section.3mf"
        assertEquals("Victorian_Christmas_street_lamp_e27_ams – lower_base_section.3mf", FileNames.fromContentDisposition(header))
    }

    @Test
    fun beideVariantenGleichzeitig() {
        val header = "attachment; filename=\"fallback.3mf\"; filename*=UTF-8''Lampe%20(2).3mf"
        assertEquals("Lampe (2).3mf", FileNames.fromContentDisposition(header))
    }

    @Test
    fun einfacheVarianten() {
        assertEquals("Benchy.stl", FileNames.fromContentDisposition("attachment; filename=\"Benchy.stl\""))
        assertEquals("Benchy.stl", FileNames.fromContentDisposition("attachment; filename=Benchy.stl"))
        assertEquals("C++ Halter.stl", FileNames.fromContentDisposition("attachment; filename*=utf-8''C%2B%2B%20Halter.stl"))
        assertNull(FileNames.fromContentDisposition(null))
        assertNull(FileNames.fromContentDisposition("inline"))
    }

    @Test
    fun repariertKaputteNamenAlterVersionen() {
        assertEquals(
            "Victorian_Christmas_street_lamp_e27_ams (2).3mf",
            FileNames.repair("Victorian_Christmas_street_lamp_e27_ams (2).3mf;filename_=utf-8\"Victorian_Christmas_street_lamp_e27_ams – lower_base_sectio"),
        )
        assertEquals("Benchy.stl", FileNames.repair("Benchy.stl"))
    }
}
