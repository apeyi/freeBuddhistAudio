package com.dharmachakra.fba_android.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The media session, playback and browse tree. A MediaLibraryService so that
 * Android Auto (and Bluetooth/Assistant) can browse and play FBA content
 * without the app's UI: they receive talk ids from [LibraryTree] and, when they
 * ask to play one, [PlaybackResolver] expands it into the chapter queue. It also
 * answers playback resumption (the notification's play button after the OS
 * killed the process) with the last talk, and [PlaybackPersistence] records
 * progress whether or not the UI is alive.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var resolver: PlaybackResolver
    @Inject lateinit var tree: LibraryTree
    @Inject lateinit var persistence: PlaybackPersistence

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val searchResults = HashMap<String, List<MediaItem>>()

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Dismiss notification when playback ends (prevents stale notification)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    if (!player.playWhenReady) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }
            }
        })
        persistence.attach(player, serviceScope)

        // PendingIntent so tapping the notification (re)launches the app — without
        // this the notification body does nothing, including after the OS kills us.
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .apply { if (sessionActivity != null) setSessionActivity(sessionActivity) }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootParams = LibraryParams.Builder().setExtras(tree.rootExtras()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(tree.root(), rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val children = tree.children(parentId)
                ?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            LibraryResult.ofItemList(ImmutableList.copyOf(children.page(page, pageSize)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        // --- Search (Android Auto voice: "play Subhuti on Free Buddhist Audio") ---

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val results = tree.search(query)
            searchResults[query] = results
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val results = searchResults[query] ?: tree.search(query).also { searchResults[query] = it }
            LibraryResult.ofItemList(ImmutableList.copyOf(results.page(page, pageSize)), params)
        }

        // --- Playing items that arrive as ids only (car, Assistant, resumption) ---

        /**
         * The app's own controller sends fully resolved chapters (with URIs);
         * other controllers send a talk id. Expand the latter into the talk's
         * chapter queue, starting at the account/local resume point.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.all { it.localConfiguration != null }) {
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
            }
            return serviceScope.future {
                val first = mediaItems.firstOrNull { it.localConfiguration == null }
                val parsed = first?.let { MediaIds.parse(it.mediaId) } as? MediaIds.Parsed.Talk
                    ?: throw IllegalArgumentException("Unplayable media id: ${first?.mediaId}")
                val prepared = resolver.prepare(parsed.catNum)
                    ?: throw IllegalStateException("Nothing playable for ${parsed.catNum}")
                val (index, position) = resolver.resumePoint(prepared.talk, prepared.items.size, parsed.trackIndex)
                MediaSession.MediaItemsWithStartPosition(prepared.items, index, position)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = serviceScope.future {
            mediaItems.flatMap { item ->
                if (item.localConfiguration != null) listOf(item)
                else (MediaIds.parse(item.mediaId) as? MediaIds.Parsed.Talk)
                    ?.let { resolver.prepare(it.catNum)?.items }
                    ?: emptyList()
            }
        }

        /**
         * The system's "resume" (media notification after a process kill, car
         * "continue listening"): rebuild the last talk at its saved position.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val catNum = resolver.lastPlayedCatNum() ?: throw IllegalStateException("Nothing to resume")
            val prepared = resolver.prepare(catNum) ?: throw IllegalStateException("Nothing playable for $catNum")
            val (index, position) = resolver.resumePoint(prepared.talk, prepared.items.size)
            MediaSession.MediaItemsWithStartPosition(prepared.items, index, position)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should NOT kill active playback (standard media-app
        // behaviour — the notification keeps controlling it). Only stop the service
        // when nothing is actually playing.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            player?.stop()
            player?.clearMediaItems()
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Ensure the foreground notification is removed when the service goes away.
        // Force-stop kills the process and skips this, but normal shutdowns will clean up.
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/** Page a list the way MediaBrowser asks for it (pageSize <= 0 means "everything"). */
private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (pageSize <= 0 || page < 0) return this
    val from = page * pageSize
    if (from >= size) return emptyList()
    return subList(from, minOf(from + pageSize, size))
}

/** Bridge a coroutine to the ListenableFuture Media3 callbacks expect. */
private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = launch {
        try {
            future.set(block())
        } catch (e: CancellationException) {
            future.cancel(false)
        } catch (e: Exception) {
            future.setException(e)
        }
    }
    future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    return future
}
