package de.k1direkt

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.k1direkt.data.Updater
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdaterTest {

    @Test
    fun nimmtHoechsteVersionStattGithubLatest() {
        // GitHub liefert sortiert nach Erstellung; 0.3.18 wurde nach 0.3.19 veröffentlicht
        val json = JSONArray(
            """[
              {"tag_name":"v0.3.18","draft":false,"prerelease":false},
              {"tag_name":"v0.3.19","draft":false,"prerelease":false},
              {"tag_name":"v0.3.17","draft":false,"prerelease":false},
              {"tag_name":"v0.3.25","draft":true,"prerelease":false},
              {"tag_name":"nightly","draft":false,"prerelease":false}
            ]"""
        )
        assertEquals("v0.3.19", Updater.newestRelease(json)?.optString("tag_name"))
    }

    @Test
    fun vergleichtVersionenNumerisch() {
        assertTrue(Updater.compareVersions(listOf(0, 3, 100), listOf(0, 3, 99)) > 0)
        assertTrue(Updater.compareVersions(listOf(0, 4), listOf(0, 3, 200)) > 0)
        assertEquals(listOf(0, 3, 19), Updater.versionOf("v0.3.19"))
        assertNull(Updater.versionOf("nightly"))
        assertNull(Updater.newestRelease(JSONArray("[]")))
    }
}
