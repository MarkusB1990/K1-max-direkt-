package de.k1direkt

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.k1direkt.sources.MakerWorldProvider
import de.k1direkt.sources.ModelSite
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MakerWorldTest {

    /** Ausschnitt einer echten Antwort von /v1/search-service/select/design2 */
    private val sample = """
        {"total":3627,"hits":[
          {"id":40146,"title":"Benchy Bambu Pla Basic","slug":"benchy-bambu-pla-basic","titleTranslated":"",
           "cover":"https://makerworld.bblmw.com/makerworld/model/US2262720d55a8f6/design/2023-10-14_994ac3c06d028.jpg",
           "likeCount":14610,"collectionCount":32267,"downloadCount":473402,
           "designCreator":{"uid":3614949154,"name":"Bambu Lab"}},
          {"id":0,"title":"kaputt"},
          {"id":123,"title":"","titleTranslated":"Übersetzt","cover":null}
        ]}
    """.trimIndent()

    @Test
    fun liestSuchergebnisse() {
        val results = MakerWorldProvider.parse(JSONObject(sample).getJSONArray("hits"))
        assertEquals(2, results.size)
        val r = results[0]
        assertEquals(ModelSite.MAKERWORLD, r.site)
        assertEquals("Benchy Bambu Pla Basic", r.title)
        assertEquals("Bambu Lab", r.author)
        assertEquals("https://makerworld.com/de/models/40146", r.pageUrl)
        assertEquals(14610, r.likes)
        assertEquals(473402, r.downloads)
        assertEquals("Übersetzt", results[1].title)
        assertEquals(null, results[1].imageUrl)
    }
}
