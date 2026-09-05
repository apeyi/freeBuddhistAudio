package com.fba.app

import com.fba.app.data.remote.SiteMenuParser
import com.fba.app.domain.model.ContentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMenuParserTest {

    // Trimmed sample of document.__FBA__.sidebar_menu as served on 2026-09-05
    private val sample = """
    {"items":[
      {"classes":["mobile-menu"],"label":"collections","link":"#","om":false,"children":[
        {"label":"latest talks & meditations","link":"/browse?cat=latest&t=audio ","om":false,"children":[]},
        {"label":"the buddha","link":"/collection/buddha","om":false,"children":[]},
        {"label":"meditation & mindfulness","link":"#meditation-and-mindfulness","om":false,"children":[
          {"label":"all meditation & mindfulness","link":"/collection/meditation-mindfulness","om":false,"children":[]},
          {"label":"body awareness","link":"/collection/body-awareness ","om":false,"children":[]}
        ]},
        {"label":"all series","link":"/series/","om":false,"children":[]},
        {"label":"all speakers","link":"/browse?cat=speakers&t=audio","om":false,"children":[]}
      ]},
      {"label":"themes","link":"#","om":false,"children":[
        {"label":"all themes","link":"/browse?cat=themes&t=audio","om":false,"children":[]},
        {"label":"bodhisattva ideal","link":"/collection/bodhisattva-ideal","om":false,"children":[]}
      ]},
      {"label":"people","link":"#","om":false,"children":[
        {"label":"amalamati (en espa&ntilde;ol)","link":"/browse?s=Amalamati","om":false,"children":[]},
        {"label":"subhuti","link":"/collection/subhuti","om":true,"children":[]}
      ]}
    ]}
    """.trimIndent()

    @Test
    fun parsesTopLevelSectionsAndChildren() {
        val menu = SiteMenuParser.parse(sample)
        assertEquals(listOf("collections", "themes", "people"), menu.map { it.label })
        assertEquals(5, menu[0].children.size)
        assertEquals(2, menu[0].children[2].children.size)
    }

    @Test
    fun unescapesEntitiesAndTrimsLinks() {
        val people = SiteMenuParser.section(SiteMenuParser.parse(sample), "people")!!
        assertEquals("amalamati (en español)", people.children[0].label)
        val latest = SiteMenuParser.parse(sample)[0].children[0]
        assertEquals("/browse?cat=latest&t=audio", latest.link)
    }

    @Test
    fun readsOmFlag() {
        val people = SiteMenuParser.section(SiteMenuParser.parse(sample), "people")!!
        assertFalse(people.children[0].om)
        assertTrue(people.children[1].om)
    }

    @Test
    fun collectionTilesFlattenOneLevelAndSkipIndexPages() {
        val tiles = SiteMenuParser.collectionTiles(SiteMenuParser.parse(sample))
        assertEquals(listOf("buddha", "meditation-mindfulness", "body-awareness"), tiles.map { it.collectionSlug })
    }

    @Test
    fun menuNodesResolveToContentSources() {
        val menu = SiteMenuParser.parse(sample)
        val collections = menu[0].children
        assertEquals(ContentSource.ApiCollection("latest", "latest talks & meditations"), collections[0].toSource())
        assertEquals(ContentSource.NamedCollection("buddha"), collections[1].toSource())
        assertNull(collections[2].toSource()) // "#" placeholder with children
        assertEquals(ContentSource.ApiCollection("speakers", "all speakers"), collections[4].toSource())
        assertEquals(ContentSource.Browse("/browse?s=Amalamati"), menu[2].children[0].toSource())
    }

    @Test
    fun contentSourceRoundTripsThroughEncoding() {
        val sources = listOf(
            ContentSource.ApiCollection("all_series", "Series"),
            ContentSource.NamedCollection("bodhisattva-ideal"),
            ContentSource.Browse("/browse?s=Vajragupta_(m)&t=audio"),
            ContentSource.Series("/series/details?num=X16"),
        )
        for (s in sources) assertEquals(s, ContentSource.decode(s.encode()))
        assertNull(ContentSource.decode("garbage"))
    }
}
