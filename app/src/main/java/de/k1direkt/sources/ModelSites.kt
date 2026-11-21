package de.k1direkt.sources

import java.net.URLEncoder

/** Alle unterstützten Modell-Plattformen. Jede kann im eingebauten Browser geöffnet werden. */
enum class ModelSite(
    val displayName: String,
    val homeUrl: String,
    val color: Long,
    private val searchTemplate: String,
    /** Hosts, deren Links im eingebauten Browser bleiben (Login, CDN, …). */
    val hosts: List<String>,
) {
    PRINTABLES(
        "Printables", "https://www.printables.com/model", 0xFFFA6831,
        "https://www.printables.com/search/models?q=%s", listOf("printables.com", "prusa3d.com"),
    ),
    THINGIVERSE(
        "Thingiverse", "https://www.thingiverse.com/", 0xFF248BFB,
        "https://www.thingiverse.com/search?q=%s&type=things&sort=relevant", listOf("thingiverse.com"),
    ),
    MAKERWORLD(
        "MakerWorld", "https://makerworld.com/de", 0xFF00AE42,
        "https://makerworld.com/de/search/models?keyword=%s", listOf("makerworld.com", "bambulab.com"),
    ),
    THANGS(
        "Thangs", "https://thangs.com/", 0xFF6E5BFF,
        "https://thangs.com/search/%s?scope=all", listOf("thangs.com"),
    ),
    CULTS3D(
        "Cults3D", "https://cults3d.com/de", 0xFF8E44AD,
        "https://cults3d.com/de/suche?q=%s", listOf("cults3d.com"),
    ),
    MYMINIFACTORY(
        "MyMiniFactory", "https://www.myminifactory.com/", 0xFF00B3C7,
        "https://www.myminifactory.com/search/?query=%s", listOf("myminifactory.com"),
    ),
    CREALITY_CLOUD(
        "Creality Cloud", "https://www.crealitycloud.com/", 0xFF1E88E5,
        "https://www.crealitycloud.com/search?keyword=%s&type=model", listOf("crealitycloud.com", "creality.com"),
    );

    fun searchUrl(query: String): String =
        searchTemplate.format(URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20"))

    companion object {
        fun forUrl(url: String): ModelSite? = entries.firstOrNull { site ->
            site.hosts.any { url.contains(it, ignoreCase = true) }
        }
    }
}

data class SearchResult(
    val site: ModelSite,
    val id: String,
    val title: String,
    val author: String?,
    val imageUrl: String?,
    val pageUrl: String,
    val likes: Int? = null,
    val downloads: Int? = null,
)
