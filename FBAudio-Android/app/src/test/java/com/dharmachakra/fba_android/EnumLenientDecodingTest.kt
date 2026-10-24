package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.api.infrastructure.Serializer
import com.dharmachakra.fba_android.api.models.ListItem
import com.dharmachakra.fba_android.api.models.ListItemKind
import com.dharmachakra.fba_android.api.models.Menu
import com.dharmachakra.fba_android.api.models.MenuNodeKind
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The load-bearing half of the API's forward-compat design: an enum value this
 * build doesn't know (one a newer server added) must decode to UNKNOWN, not fail
 * the whole response. Verifies the hardening applied by scripts/harden-enums.py.
 */
class EnumLenientDecodingTest {
    private val json = Serializer.kotlinxSerializationJson

    private fun listItem(kind: String) = """
        {"kind":"$kind","id":"X1","title":"T","subtitle":null,"image":null,"year":null,
         "language":null,"speaker":null,"place":null,"placeName":null,"omOnly":false,
         "talkCount":null,"hasRemaster":null,"webUrl":"https://x"}
    """.trimIndent()

    @Test fun knownKindDecodes() {
        assertEquals(ListItemKind.TALK, json.decodeFromString<ListItem>(listItem("talk")).kind)
    }

    @Test fun unknownKindBecomesUnknown() {
        // "podcast" is not in this build's ListItemKind — must not throw.
        assertEquals(ListItemKind.UNKNOWN, json.decodeFromString<ListItem>(listItem("podcast")).kind)
    }

    @Test fun unknownMenuKindInAListStillParsesTheWholeResponse() {
        // A single new menu kind must not blank the entire /menu.
        val menuJson = """
            {"items":[
              {"id":"1","label":"A","kind":"talk","target":"LOC1","language":null,"omOnly":false,
               "image":null,"country":null,"webUrl":"https://x","children":[]},
              {"id":"2","label":"B","kind":"collection","target":"c","language":null,"omOnly":false,
               "image":null,"country":null,"webUrl":"https://y","children":[]}
            ]}
        """.trimIndent()
        val menu = json.decodeFromString<Menu>(menuJson)
        assertEquals(2, menu.items.size)
        assertEquals(MenuNodeKind.UNKNOWN, menu.items[0].kind)      // "talk" unknown in draft.4
        assertEquals(MenuNodeKind.COLLECTION, menu.items[1].kind)
    }
}
