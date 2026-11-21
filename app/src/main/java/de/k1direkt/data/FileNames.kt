package de.k1direkt.data

import java.net.URLDecoder

/**
 * Ermittelt den Dateinamen eines Downloads. Versteht auch die RFC-5987-Form
 * `filename*=UTF-8''…` (MakerWorld, Printables), an der Androids URLUtil scheitert.
 */
object FileNames {

    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // filename*=UTF-8''Name%20mit%20Leerzeichen.3mf
        Regex("""filename\*\s*=\s*([\w-]*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            val charset = m.groupValues[1].ifBlank { "UTF-8" }
            val raw = m.groupValues[2].trim().trim('"')
            return runCatching { URLDecoder.decode(raw.replace("+", "%2B"), charset) }.getOrDefault(raw).clean()
        }
        // filename="Name.3mf"
        Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1].clean() }
        // filename=Name.3mf
        Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1].trim().clean() }
        return null
    }

    /** Repariert Namen, in die ein Content-Disposition-Rest geraten ist (alte App-Versionen). */
    fun repair(name: String): String {
        val cut = Regex(""";\s*filename""", RegexOption.IGNORE_CASE).find(name)?.range?.first ?: return name
        return name.substring(0, cut).trim().ifBlank { name }
    }

    private fun String.clean(): String = substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { "download" }
}
