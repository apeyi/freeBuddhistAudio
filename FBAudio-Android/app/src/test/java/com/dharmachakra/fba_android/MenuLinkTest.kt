package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.api.models.MenuNodeKind
import com.dharmachakra.fba_android.data.remote.api.V2Catalogue
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.MenuNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v2 menu kinds (draft.5) → the domain link shapes the navigation routes on. */
class MenuLinkTest {

    private fun node(kind: MenuNodeKind, target: String?, webUrl: String? = null) =
        MenuNode(label = "x", link = V2Catalogue.menuLink(kind, target, webUrl))

    @Test fun talkKindOpensTheTalk() {
        val n = node(MenuNodeKind.TALK, "LOC3883")
        assertEquals("LOC3883", n.talkCatNum)
        assertNull(n.toSource())
        assertEquals(false, n.isExternal)
    }

    @Test fun indexKindOpensTheFullIndex() {
        for (target in listOf("speakers", "places", "years", "themes")) {
            val n = node(MenuNodeKind.INDEX, target)
            assertEquals(ContentSource.ApiCollection(target, "x"), n.toSource())
            assertNull(n.talkCatNum)
        }
    }

    @Test fun existingKindsUnchanged() {
        assertEquals(ContentSource.NamedCollection("ethics"), node(MenuNodeKind.COLLECTION, "ethics").toSource())
        assertEquals(ContentSource.Series("/series/details?num=X06"), node(MenuNodeKind.SERIES, "X06").toSource())
        assertEquals(ContentSource.Browse("/browse?p=Adhisthana"), node(MenuNodeKind.PLACE, "Adhisthana").toSource())
        assertEquals("#", V2Catalogue.menuLink(MenuNodeKind.GROUP, null, null))
        assertEquals("https://example.org/about", V2Catalogue.menuLink(MenuNodeKind.PAGE, null, "https://example.org/about"))
        assertEquals("#", V2Catalogue.menuLink(MenuNodeKind.UNKNOWN, "t", null))
    }

    @Test fun talkCatNumOnlyForTalkLinks() {
        assertNull(MenuNode("a", "/series/details?num=X06").talkCatNum)
        assertEquals("LOC7132", MenuNode("a", "https://www.freebuddhistaudio.com/audio/details?num=LOC7132#x").talkCatNum)
    }
}
