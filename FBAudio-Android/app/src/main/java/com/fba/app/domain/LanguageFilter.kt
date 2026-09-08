package com.fba.app.domain

import com.fba.app.domain.model.MenuNode
import com.fba.app.domain.model.SearchResult

/**
 * "English | All languages" support built on the markers FBA already maintains:
 *
 *  1. Curated menu labels carry a language suffix — "(deutsch)", "(en español)"…
 *     Bilingual entries ("deutsch | english") count as English.
 *  2. Places labels name the country — "(españa)", "(deutschland)"… Talks carry
 *     their centre, so talks from those centres can be hidden.
 *  3. Speakers listed under the Languages section, or marked per (1).
 *
 * The search API's language code is NOT used — it is unreliable (Spanish talks
 * are tagged "en"). India is deliberately left visible: talks there are in
 * English or Hindi and can't be told apart from the markers.
 */
object LanguageFilter {

    private val languageMarkers = listOf(
        "deutsch", "español", "espanol", "nederlands", "svenska", "français", "francais",
        "हिंदी", "polsku", "português", "portugues", "norsk", "русском", "suomeksi",
        "italiano", "auf deutsch", "en français",
    )

    private val nonEnglishCountryMarkers = listOf(
        "españa", "espana", "deutschland", "méxico", "mexico", "nederland", "vlaams",
        "france", "norge", "sverige", "brasil", "россия", "krakow", "kraków", "polska",
    )

    /** Does a curated menu label mark a non-English entry (and not a bilingual one)? */
    fun isNonEnglishLabel(label: String): Boolean {
        val l = label.lowercase()
        if (l.contains("english")) return false
        return languageMarkers.any { l.contains(it) }
    }

    /** Does a Places label name a non-English-speaking country? */
    fun isNonEnglishPlaceLabel(label: String): Boolean {
        val l = label.lowercase()
        return nonEnglishCountryMarkers.any { l.contains(it) }
    }

    /** Speaker names (lowercase) marked non-English in the People and Languages sections. */
    fun nonEnglishSpeakers(menu: List<MenuNode>): Set<String> {
        val out = mutableSetOf<String>()
        menu.firstOrNull { it.label.equals("people", ignoreCase = true) }?.children?.forEach { node ->
            if (isNonEnglishLabel(node.label)) speakerFromLink(node.link)?.let { out.add(it) }
        }
        menu.firstOrNull { it.label.equals("languages", ignoreCase = true) }?.children?.forEach { node ->
            speakerFromLink(node.link)?.let { out.add(it) }
        }
        return out
    }

    /** Centre names (lowercase) from Places entries in non-English-speaking countries. */
    fun nonEnglishCentres(menu: List<MenuNode>): Set<String> {
        val out = mutableSetOf<String>()
        fun visit(node: MenuNode) {
            if (isNonEnglishPlaceLabel(node.label)) {
                placeFromLink(node.link)?.let { out.add(it) }
                // "barcelona (españa)" → also match the city name itself
                out.add(node.label.substringBefore('(').trim().lowercase())
            }
            node.children.forEach { visit(it) }
        }
        menu.firstOrNull { it.label.equals("places", ignoreCase = true) }?.children?.forEach { visit(it) }
        menu.firstOrNull { it.label.equals("languages", ignoreCase = true) }?.children?.forEach { node ->
            placeFromLink(node.link)?.let { out.add(it) }
        }
        return out
    }

    /** Filter menu entries for the English-only setting. */
    fun filterMenu(nodes: List<MenuNode>, englishOnly: Boolean): List<MenuNode> {
        if (!englishOnly) return nodes
        return nodes.filterNot { isNonEnglishLabel(it.label) }
            .map { if (it.hasChildren) it.copy(children = filterMenu(it.children, true)) else it }
    }

    /** Filter talk/series items for the English-only setting. */
    fun filterItems(
        items: List<SearchResult>,
        englishOnly: Boolean,
        nonEnglishSpeakers: Set<String>,
        nonEnglishCentres: Set<String>,
    ): List<SearchResult> {
        if (!englishOnly) return items
        return items.filterNot { item ->
            val speaker = item.speaker.trim().lowercase()
            val centre = item.centre.trim().lowercase()
            (speaker.isNotBlank() && speaker in nonEnglishSpeakers) ||
                (centre.isNotBlank() && nonEnglishCentres.any { centre == it || centre.contains(it) }) ||
                isNonEnglishLabel(item.title)
        }
    }

    /** "/browse?s=Amalamati" → "amalamati" */
    fun speakerFromLink(link: String): String? =
        Regex("[?&]s=([^&#]+)").find(link)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
            ?.replace('_', ' ')?.trim()?.lowercase()

    /** "/browse?p=Triratna_Barcelona" → "triratna barcelona" */
    fun placeFromLink(link: String): String? =
        Regex("[?&]p=([^&#]+)").find(link)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
            ?.replace('_', ' ')?.trim()?.lowercase()
}
