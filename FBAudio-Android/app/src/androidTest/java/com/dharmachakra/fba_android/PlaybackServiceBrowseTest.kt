package com.dharmachakra.fba_android

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dharmachakra.fba_android.player.MediaIds
import com.dharmachakra.fba_android.player.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Connects to PlaybackService exactly the way Android Auto does (a MediaBrowser
 * on the media session) and walks the browse tree, then asks it to play a talk
 * by id and checks the chapter queue is built. Needs a device and network.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceBrowseTest {

    private lateinit var browser: MediaBrowser
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun connect() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browser = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
    }

    @After
    fun release() {
        instrumentation.runOnMainSync { browser.stop(); browser.clearMediaItems(); browser.release() }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun rootHasTheFourTabs() {
        val root = onMain { browser.getLibraryRoot(null) }.get(15, TimeUnit.SECONDS).value!!
        assertEquals(MediaIds.ROOT, root.mediaId)
        val tabs = onMain { browser.getChildren(root.mediaId, 0, 50, null) }.get(15, TimeUnit.SECONDS).value!!
        assertEquals(MediaIds.FOLDERS, tabs.map { it.mediaId })
        assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
    }

    @Test
    fun sangharakshitaListsSeriesThenTalks() {
        val series = onMain { browser.getChildren(MediaIds.SANGHARAKSHITA, 0, 100, null) }.get(30, TimeUnit.SECONDS).value!!
        assertTrue("expected 23 series, got ${series.size}", series.size >= 20)
        val talks = onMain { browser.getChildren(series.first().mediaId, 0, 100, null) }.get(60, TimeUnit.SECONDS).value!!
        assertTrue("series ${series.first().mediaId} has no talks", talks.isNotEmpty())
        assertTrue(talks.all { it.mediaMetadata.isPlayable == true && it.mediaId.startsWith("talk/") })
    }

    @Test
    fun playingATalkIdBuildsTheChapterQueue() {
        // "Who is the Buddha?" (Sangharakshita, 1968)
        onMain {
            browser.setMediaItem(MediaItem.Builder().setMediaId(MediaIds.talk("01")).build())
            browser.prepare()
        }
        val deadline = System.currentTimeMillis() + 60_000
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            count = onMain { browser.mediaItemCount }
            if (count > 0) break
            Thread.sleep(500)
        }
        assertTrue("queue not built within 60 s", count >= 1)
        val first = onMain { browser.getMediaItemAt(0) }
        assertEquals("talk/01/0", first.mediaId)
        assertEquals("Who is the Buddha?", first.mediaMetadata.title.toString())
    }
}
