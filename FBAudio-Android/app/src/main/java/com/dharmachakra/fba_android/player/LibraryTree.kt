package com.dharmachakra.fba_android.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dharmachakra.fba_android.R
import com.dharmachakra.fba_android.data.local.DownloadDao
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.data.local.RecentlyListenedDao
import com.dharmachakra.fba_android.data.repository.ContentRepository
import com.dharmachakra.fba_android.data.repository.TalkRepository
import com.dharmachakra.fba_android.domain.model.ContentSource
import com.dharmachakra.fba_android.domain.model.SangharakshitaData
import com.dharmachakra.fba_android.domain.model.SearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The browse tree offered to Android Auto (and any MediaBrowser):
 *
 *   Recently listened · Downloads · Sangharakshita → series → talks · Latest
 *
 * Folders are browsable, talks are playable. A talk item carries only its media
 * id; when the car asks to play it, [PlaybackResolver] expands it to the
 * chapter queue. Everything here reads what the app already has (Room, the
 * bundled catalogue, the cached website content), so it works offline for the
 * first three tabs.
 */
@Singleton
class LibraryTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentlyListenedDao: RecentlyListenedDao,
    private val downloadDao: DownloadDao,
    private val content: ContentRepository,
    private val talks: TalkRepository,
) {
    companion object {
        // Android Auto content-style hints (androidx.media.utils.MediaConstants values)
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_LIST = 1
        const val CONTENT_STYLE_GRID = 2
        private const val MAX_RECENT = 20
    }

    /** Extras for the root: lists for both folders and talks. */
    fun rootExtras(): Bundle = Bundle().apply {
        putBoolean(CONTENT_STYLE_SUPPORTED, true)
        putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
    }

    fun root(): MediaItem = folder(MediaIds.ROOT, "Free Buddhist Audio", null)

    /** Children of a folder, or null when the id isn't a folder we know. */
    suspend fun children(parentId: String): List<MediaItem>? {
        return when (val parsed = MediaIds.parse(parentId)) {
            MediaIds.Parsed.Root -> listOf(
                folder(MediaIds.RECENT, "Recently listened", R.drawable.ic_auto_recent),
                folder(MediaIds.DOWNLOADS, "Downloads", R.drawable.ic_auto_download),
                folder(MediaIds.SANGHARAKSHITA, "Sangharakshita", R.drawable.ic_auto_person),
                folder(MediaIds.LATEST, "Latest", R.drawable.ic_auto_latest),
            )
            is MediaIds.Parsed.Folder -> when (parsed.key) {
                MediaIds.RECENT -> recentlyListenedDao.getAllOnce()
                    .sortedByDescending { it.listenedAt }
                    .take(MAX_RECENT)
                    .map { talk(it.catNum, it.title, it.speaker, it.imageUrl) }
                MediaIds.DOWNLOADS -> downloadDao.getAllDownloadsOnce()
                    .filter { it.status == DownloadStatus.COMPLETE && it.filePath.isNotBlank() }
                    .sortedByDescending { it.downloadedAt }
                    .map { talk(it.catNum, it.title, it.speaker, it.imageUrl) }
                MediaIds.SANGHARAKSHITA -> SangharakshitaData.series.map { s ->
                    folder(MediaIds.series(s.id), s.title, null, grid = false)
                }
                MediaIds.LATEST -> safely { content.getPage(ContentSource.ApiCollection("latest", "Latest"), 1).items }
                    .filter { it.isTalk }
                    .map { talk(it) }
                else -> null
            }
            is MediaIds.Parsed.Series -> safely {
                content.getPage(ContentSource.seriesByCatNum(parsed.id), 1).items
            }.filter { it.isTalk }.map { talk(it) }
            is MediaIds.Parsed.Talk -> emptyList() // talks aren't browsable
            null -> null
        }
    }

    /** A single item by id (folders and talks; talks are looked up in the catalogue). */
    suspend fun item(mediaId: String): MediaItem? {
        return when (val parsed = MediaIds.parse(mediaId)) {
            MediaIds.Parsed.Root -> root()
            is MediaIds.Parsed.Folder, is MediaIds.Parsed.Series ->
                children(MediaIds.ROOT)?.firstOrNull { it.mediaId == mediaId }
                    ?: children(MediaIds.SANGHARAKSHITA)?.firstOrNull { it.mediaId == mediaId }
            is MediaIds.Parsed.Talk -> {
                val t = talks.getTalkDetail(parsed.catNum) ?: return null
                talk(t.catNum, t.title, t.speaker, t.imageUrl)
            }
            null -> null
        }
    }

    /** Voice search from the car ("play Subhuti on Free Buddhist Audio"): talks only. */
    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        return safely { talks.searchAudio(query) }.filter { it.isTalk }.take(30).map { talk(it) }
    }

    private suspend fun <T> safely(block: suspend () -> List<T>): List<T> =
        try { block() } catch (_: Exception) { emptyList() }

    // --- item builders ---

    private fun folder(id: String, title: String, iconRes: Int?, grid: Boolean = false): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, if (grid) CONTENT_STYLE_GRID else CONTENT_STYLE_LIST)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                    .setArtworkUri(iconRes?.let { Uri.parse("android.resource://${context.packageName}/$it") })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun talk(item: SearchResult) = talk(item.catNum, item.title, item.speaker, item.imageUrl)

    fun talk(catNum: String, title: String, speaker: String, imageUrl: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(MediaIds.talk(catNum))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(speaker)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setArtworkUri(imageUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .build()
            )
            .build()
}
