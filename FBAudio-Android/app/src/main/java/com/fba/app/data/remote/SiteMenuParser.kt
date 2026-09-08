package com.fba.app.data.remote

import com.fba.app.domain.model.MenuNode
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.parser.Parser

/**
 * Parses the website's `document.__FBA__.sidebar_menu` JSON into [MenuNode]s.
 * Pure — no network — so it can be unit tested against a captured JSON sample.
 */
object SiteMenuParser {

    fun parse(sidebarMenuJson: String): List<MenuNode> {
        val root = JsonParser.parseString(sidebarMenuJson)
        val items = when {
            root.isJsonObject -> root.asJsonObject.getAsJsonArray("items")
            root.isJsonArray -> root.asJsonArray
            else -> null
        } ?: return emptyList()
        return items.mapNotNull { parseNode(it) }
    }

    private fun parseNode(el: JsonElement): MenuNode? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val label = Parser.unescapeEntities(obj.str("label") ?: obj.str("name") ?: return null, false).trim()
        val link = (obj.str("link") ?: obj.str("href") ?: "").trim()
        val om = obj.has("om") && !obj.get("om").isJsonNull && obj.get("om").let {
            it.isJsonPrimitive && (it.asJsonPrimitive.isBoolean && it.asBoolean || it.asJsonPrimitive.isNumber && it.asInt != 0)
        }
        val childrenArr = obj.getAsJsonArrayOrNull("children") ?: obj.getAsJsonArrayOrNull("subMenu")
        val children = childrenArr?.mapNotNull { parseNode(it) } ?: emptyList()
        return MenuNode(label = label, link = link, om = om, children = children)
    }

    /** Find the top-level section by label (case-insensitive), e.g. "themes", "people". */
    fun section(menu: List<MenuNode>, label: String): MenuNode? =
        menu.firstOrNull { it.label.equals(label, ignoreCase = true) }

    /**
     * The curated collections shown as tiles: every `/collection/` entry under the
     * "collections" section, flattened one level (Meditation & Mindfulness and
     * Introducing Buddhism have sub-collections). Index pages ("all series",
     * "all speakers"…) and "latest" are excluded — they have their own rows.
     */
    fun collectionTiles(menu: List<MenuNode>): List<MenuNode> {
        val section = section(menu, "collections") ?: return emptyList()
        val out = mutableListOf<MenuNode>()
        for (node in section.children) {
            if (node.collectionSlug != null) out.add(node)
            for (child in node.children) {
                if (child.collectionSlug != null) out.add(child)
            }
        }
        return out.distinctBy { it.collectionSlug }
    }

    private fun JsonObject.str(key: String): String? =
        if (has(key) && !get(key).isJsonNull && get(key).isJsonPrimitive) get(key).asString else null

    private fun JsonObject.getAsJsonArrayOrNull(key: String) =
        if (has(key) && get(key).isJsonArray) getAsJsonArray(key) else null
}
