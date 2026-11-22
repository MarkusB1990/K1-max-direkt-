package de.k1direkt.sources

import de.k1direkt.data.AppSettings
import de.k1direkt.data.Http
import de.k1direkt.data.getText
import de.k1direkt.data.readable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Eine Plattform, die per API durchsucht werden kann. */
interface SearchProvider {
    val site: ModelSite

    /** Liefert null, wenn der Anbieter wegen fehlender Zugangsdaten übersprungen wird. */
    fun missingConfig(settings: AppSettings): String?

    suspend fun search(query: String, page: Int, settings: AppSettings): List<SearchResult>

    /** Gesamtzahl der Treffer der letzten Suche, falls die Plattform sie liefert. */
    val lastTotal: Int? get() = null
}

data class ProviderOutcome(
    val site: ModelSite,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
    val skipped: String? = null,
    /** Gesamtzahl der Treffer bei der Plattform (falls bekannt) */
    val total: Int? = null,
    /** Keine weiteren Seiten mehr verfügbar */
    val exhausted: Boolean = false,
)

object UnifiedSearch {
    val providers: List<SearchProvider> = listOf(
        MakerWorldProvider,
        PrintablesProvider,
        ThingiverseProvider,
        Cults3DProvider,
        MyMiniFactoryProvider,
    )

    /** Fragt alle (bzw. die gewählten) Anbieter parallel ab; Fehler einzelner Seiten brechen die Suche nicht ab. */
    suspend fun search(query: String, page: Int, settings: AppSettings, sites: Set<ModelSite>? = null): List<ProviderOutcome> = coroutineScope {
        providers.filter { sites == null || it.site in sites }.map { p ->
            async {
                val missing = p.missingConfig(settings)
                if (missing != null) {
                    ProviderOutcome(p.site, skipped = missing)
                } else {
                    try {
                        val results = withTimeout(25_000) { p.search(query, page, settings) }
                        ProviderOutcome(p.site, results = results, total = p.lastTotal, exhausted = results.isEmpty())
                    } catch (e: Exception) {
                        ProviderOutcome(p.site, error = e.readable())
                    }
                }
            }
        }.map { it.await() }
    }
}

private val JSON = "application/json; charset=utf-8".toMediaType()
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/**
 * MakerWorld (Bambu Lab) – keine offizielle API; genutzt wird die Such-Schnittstelle, die auch
 * die Bambu-Handy-App verwendet (api.bambulab.com, ohne Login). Kann sich ohne Ankündigung ändern.
 */
object MakerWorldProvider : SearchProvider {
    override val site = ModelSite.MAKERWORLD
    private const val PAGE_SIZE = 24

    override fun missingConfig(settings: AppSettings): String? = null

    override suspend fun search(query: String, page: Int, settings: AppSettings): List<SearchResult> {
        val req = Request.Builder()
            .url("https://api.bambulab.com/v1/search-service/select/design2?keyword=${enc(query)}&limit=$PAGE_SIZE&offset=${page * PAGE_SIZE}")
            .header("User-Agent", Http.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val json = JSONObject(Http.client.getText(req))
        total = json.optInt("total", -1).takeIf { it >= 0 }
        return parse(json.optJSONArray("hits") ?: JSONArray())
    }

    @Volatile private var total: Int? = null
    override val lastTotal: Int? get() = total

    internal fun parse(hits: JSONArray): List<SearchResult> = hits.objects().mapNotNull { o ->
        val id = o.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
        SearchResult(
            site = site,
            id = id.toString(),
            title = o.str("title") ?: o.str("titleTranslated") ?: "Modell $id",
            author = o.optJSONObject("designCreator")?.str("name"),
            imageUrl = o.str("cover"),
            pageUrl = "https://makerworld.com/de/models/$id",
            likes = o.intOrNull("likeCount"),
            downloads = o.intOrNull("downloadCount"),
        )
    }
}

/** Printables (Prusa) – öffentliche GraphQL-Schnittstelle, kein Schlüssel nötig. */
object PrintablesProvider : SearchProvider {
    override val site = ModelSite.PRINTABLES
    @Volatile private var total: Int? = null
    override val lastTotal: Int? get() = total
    private const val PAGE_SIZE = 24
    private val QUERY = """
        query SearchModels(${'$'}query: String!, ${'$'}limit: Int, ${'$'}offset: Int) {
          result: searchPrints2(query: ${'$'}query, limit: ${'$'}limit, offset: ${'$'}offset) {
            totalCount
            items { id name slug likesCount downloadCount image { filePath } user { publicUsername } }
          }
        }
    """.trimIndent()

    override fun missingConfig(settings: AppSettings): String? = null

    override suspend fun search(query: String, page: Int, settings: AppSettings): List<SearchResult> {
        val body = JSONObject()
            .put("operationName", "SearchModels")
            .put("query", QUERY)
            .put("variables", JSONObject().put("query", query).put("limit", PAGE_SIZE).put("offset", page * PAGE_SIZE))
        val req = Request.Builder()
            .url("https://api.printables.com/graphql/")
            .header("User-Agent", Http.USER_AGENT)
            .header("Origin", "https://www.printables.com")
            .header("Referer", "https://www.printables.com/")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val json = JSONObject(Http.client.getText(req))
        json.optJSONArray("errors")?.let { errs ->
            if (errs.length() > 0) throw IllegalStateException(errs.optJSONObject(0)?.optString("message") ?: "GraphQL-Fehler")
        }
        val result = json.optJSONObject("data")?.optJSONObject("result")
        total = result?.optInt("totalCount", -1)?.takeIf { it >= 0 }
        val items = result?.optJSONArray("items") ?: JSONArray()
        return items.objects().map { o ->
            val id = o.optString("id")
            val slug = o.optString("slug")
            SearchResult(
                site = site,
                id = id,
                title = o.optString("name"),
                author = o.optJSONObject("user")?.str("publicUsername"),
                imageUrl = o.optJSONObject("image")?.str("filePath")?.let { "https://media.printables.com/$it" },
                pageUrl = "https://www.printables.com/model/$id" + if (slug.isNotBlank()) "-$slug" else "",
                likes = o.intOrNull("likesCount"),
                downloads = o.intOrNull("downloadCount"),
            )
        }
    }
}

/** Thingiverse – offizielle REST-API, benötigt ein kostenloses App-Token. */
object ThingiverseProvider : SearchProvider {
    override val site = ModelSite.THINGIVERSE
    @Volatile private var total: Int? = null
    override val lastTotal: Int? get() = total

    override fun missingConfig(settings: AppSettings) =
        if (settings.thingiverseToken.isBlank()) "App-Token in den Einstellungen eintragen" else null

    override suspend fun search(query: String, page: Int, settings: AppSettings): List<SearchResult> {
        val req = Request.Builder()
            .url("https://api.thingiverse.com/search/${enc(query).replace("+", "%20")}/?type=things&per_page=24&page=${page + 1}&sort=relevant")
            .header("Authorization", "Bearer ${settings.thingiverseToken}")
            .build()
        val text = Http.client.getText(req)
        val hits = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text).also {
            total = it.optInt("total", -1).takeIf { t -> t >= 0 }
        }.optJSONArray("hits") ?: JSONArray()
        return hits.objects().map { o ->
            val id = o.optString("id")
            SearchResult(
                site = site,
                id = id,
                title = o.optString("name"),
                author = o.optJSONObject("creator")?.str("name"),
                imageUrl = o.str("preview_image") ?: o.str("thumbnail"),
                pageUrl = o.str("public_url") ?: "https://www.thingiverse.com/thing:$id",
                likes = o.intOrNull("like_count"),
                downloads = o.intOrNull("download_count"),
            )
        }
    }
}

/** Cults3D – GraphQL-API, benötigt Benutzername + API-Schlüssel (kostenlos im Profil). */
object Cults3DProvider : SearchProvider {
    override val site = ModelSite.CULTS3D
    private const val PAGE_SIZE = 24
    private val QUERY = """
        query Search(${'$'}q: String!, ${'$'}limit: Int, ${'$'}offset: Int) {
          creationsSearchBatch(query: ${'$'}q, limit: ${'$'}limit, offset: ${'$'}offset) {
            results { identifier name url illustrationImageUrl likesCount downloadsCount creator { nick } }
          }
        }
    """.trimIndent()

    override fun missingConfig(settings: AppSettings) =
        if (settings.cultsUser.isBlank() || settings.cultsApiKey.isBlank()) "Benutzer + API-Key in den Einstellungen eintragen" else null

    override suspend fun search(query: String, page: Int, settings: AppSettings): List<SearchResult> {
        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", JSONObject().put("q", query).put("limit", PAGE_SIZE).put("offset", page * PAGE_SIZE))
        val req = Request.Builder()
            .url("https://cults3d.com/graphql")
            .header("Authorization", Credentials.basic(settings.cultsUser, settings.cultsApiKey))
            .post(body.toString().toRequestBody(JSON))
            .build()
        val json = JSONObject(Http.client.getText(req))
        json.optJSONArray("errors")?.let { errs ->
            if (errs.length() > 0) throw IllegalStateException(errs.optJSONObject(0)?.optString("message") ?: "GraphQL-Fehler")
        }
        val results = json.optJSONObject("data")?.optJSONObject("creationsSearchBatch")?.optJSONArray("results") ?: JSONArray()
        return results.objects().map { o ->
            SearchResult(
                site = site,
                id = o.optString("identifier"),
                title = o.optString("name"),
                author = o.optJSONObject("creator")?.str("nick"),
                imageUrl = o.str("illustrationImageUrl"),
                pageUrl = o.str("url") ?: "https://cults3d.com",
                likes = o.intOrNull("likesCount"),
                downloads = o.intOrNull("downloadsCount"),
            )
        }
    }
}

/** MyMiniFactory – REST-API v2, benötigt einen API-Key. */
object MyMiniFactoryProvider : SearchProvider {
    override val site = ModelSite.MYMINIFACTORY
    @Volatile private var total: Int? = null
    override val lastTotal: Int? get() = total

    override fun missingConfig(settings: AppSettings) =
        if (settings.myMiniFactoryKey.isBlank()) "API-Key in den Einstellungen eintragen" else null

    override suspend fun search(query: String, page: Int, settings: AppSettings): List<SearchResult> {
        val req = Request.Builder()
            .url("https://www.myminifactory.com/api/v2/search?q=${enc(query)}&per_page=24&page=${page + 1}&key=${enc(settings.myMiniFactoryKey)}")
            .header("Accept", "application/json")
            .build()
        val json = JSONObject(Http.client.getText(req))
        total = json.optInt("total_count", -1).takeIf { it >= 0 }
        val items = json.optJSONArray("items") ?: JSONArray()
        return items.objects().map { o ->
            val img = o.optJSONArray("images")?.optJSONObject(0)
            SearchResult(
                site = site,
                id = o.optString("id"),
                title = o.optString("name"),
                author = o.optJSONObject("designer")?.str("name") ?: o.optJSONObject("designer")?.str("username"),
                imageUrl = img?.optJSONObject("thumbnail")?.str("url") ?: img?.optJSONObject("standard")?.str("url"),
                pageUrl = o.str("url") ?: "https://www.myminifactory.com/object/${o.optString("id")}",
                likes = o.intOrNull("likes"),
                downloads = o.intOrNull("downloads"),
            )
        }
    }
}
