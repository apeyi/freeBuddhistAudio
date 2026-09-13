package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.player.MediaIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIdsTest {

    @Test
    fun buildsAndParsesTalkIds() {
        assertEquals("talk/LOC3883", MediaIds.talk("LOC3883"))
        assertEquals(MediaIds.Parsed.Talk("LOC3883"), MediaIds.parse("talk/LOC3883"))
        assertEquals("talk/131/4", MediaIds.chapter("131", 4))
        assertEquals(MediaIds.Parsed.Talk("131", 4), MediaIds.parse("talk/131/4"))
    }

    @Test
    fun buildsAndParsesFoldersAndSeries() {
        assertEquals(MediaIds.Parsed.Root, MediaIds.parse("root"))
        for (f in MediaIds.FOLDERS) assertEquals(MediaIds.Parsed.Folder(f), MediaIds.parse(f))
        assertEquals("series/X16", MediaIds.series("X16"))
        assertEquals(MediaIds.Parsed.Series("X16"), MediaIds.parse("series/X16"))
    }

    @Test
    fun rejectsMalformedIds() {
        assertNull(MediaIds.parse(""))
        assertNull(MediaIds.parse("talk/"))
        assertNull(MediaIds.parse("talk/131/notanumber"))
        assertNull(MediaIds.parse("talk/131/-1"))
        assertNull(MediaIds.parse("album/1"))
        assertNull(MediaIds.parse("series/"))
    }
}
