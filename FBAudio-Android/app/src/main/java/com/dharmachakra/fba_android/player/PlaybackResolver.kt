package com.dharmachakra.fba_android.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dharmachakra.fba_android.data.auth.AuthRepository
import com.dharmachakra.fba_android.data.local.AppSettings
import com.dharmachakra.fba_android.data.local.DownloadEntity
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.data.repository.DownloadRepository
import com.dharmachakra.fba_android.data.repository.TalkRepository
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.download.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a talk into the queue the player actually plays: one MediaItem per
 * chapter with a resolved URI (downloaded file first, else the stream — the
 * remastered one when chosen), plus where to start (the account's checkpoint,
 * else the locally saved position). Shared by the in-app player and the
 * media service, so Android Auto, the notification's resume button and the
 * app all build the same queue.
 */
@Singleton
class PlaybackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val talkRepository: TalkRepository,
    private val downloadRepository: DownloadRepository,
    private val settings: AppSettings,
    private val auth: AuthRepository,
) {
    companion object {
        const val PREFS = "player_prefs"
        /** MediaMetadata.extras keys carried on every queued chapter */
        const val EXTRA_CAT_NUM = "catNum"
        const val EXTRA_TRACK_INDEX = "trackIndex"
        const val EXTRA_TRACK_ID = "trackId"
        /** Resume 10 s before the saved position — a short repeat re-establishes context. Same on iOS. */
        const val RESUME_REWIND_MS = 10_000L
    }

    val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Prepared(
        val talk: Talk,
        val items: List<MediaItem>,
        val download: DownloadEntity?,
        val useRemaster: Boolean,
        /** Audio is stored offline, so the stored version is what plays. */
        val versionLocked: Boolean,
    )

    /**
     * Load a talk and build its queue. Falls back to a bare Talk from the
     * download row when the network fails, so downloaded talks play offline.
     * Returns null only when nothing is playable.
     */
    suspend fun prepare(catNum: String, useRemasterOverride: Boolean? = null): Prepared? {
        // Logged in: fetch fresh so the page carries the account's saved position.
        val talk = if (auth.isLoggedIn) {
            try { talkRepository.fetchTalkDetail(catNum, forceRefresh = true) } catch (_: Exception) { null }
                ?: talkRepository.getTalkDetail(catNum)
        } else talkRepository.getTalkDetail(catNum)
        val download = downloadRepository.getDownload(catNum)

        val effectiveTalk = talk ?: Talk(
            catNum = catNum,
            title = download?.title ?: "",
            speaker = download?.speaker ?: "",
            year = 0, genre = "", durationSeconds = 0,
            imageUrl = download?.imageUrl ?: "",
            audioUrl = "",
            description = "",
        )
        val useRemaster = effectiveTalk.hasRemaster && (useRemasterOverride ?: settings.useRemaster(catNum))
        val items = buildMediaItems(effectiveTalk, download, useRemaster)
        if (items.isEmpty()) return null
        return Prepared(effectiveTalk, items, download, useRemaster, hasOfflineAudio(catNum, download))
    }

    /**
     * Where to start: [requestedTrack] if given; otherwise the account's
     * checkpoint (always wins when logged in), else the local saved position.
     * Position is only applied to the chapter it was saved against.
     */
    fun resumePoint(talk: Talk, itemCount: Int, requestedTrack: Int? = null): Pair<Int, Long> {
        var savedTrack = prefs.getInt("last_track_index_${talk.catNum}", 0)
        var savedPos = prefs.getLong("last_position_${talk.catNum}", 0)
        talk.checkpoint?.let { cp ->
            val cpIndex = talk.tracks.indexOfFirst { it.trackId == cp.trackId }
            if (cpIndex >= 0) {
                savedTrack = cpIndex
                savedPos = cp.timeSeconds * 1000L
            }
        }
        val startIndex = (requestedTrack ?: savedTrack).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val startPos = if (startIndex == savedTrack && savedPos > RESUME_REWIND_MS) savedPos - RESUME_REWIND_MS else C.TIME_UNSET
        return startIndex to startPos
    }

    /** The talk to offer for playback resumption (notification / car "resume"), if any. */
    fun lastPlayedCatNum(): String? = prefs.getString("last_cat_num", null)

    /**
     * Resolve the playable URI for one chapter, preferring offline files:
     * per-chapter download file, then (chapter 0 only) the whole-talk download
     * file, then the stream URL.
     */
    fun resolveTrackUri(catNum: String, trackIndex: Int, streamUrl: String, download: DownloadEntity?): Uri? {
        val trackFile = File(DownloadWorker.trackFilePath(context, catNum, trackIndex))
        if (trackFile.exists()) return Uri.fromFile(trackFile)
        if (trackIndex == 0 && download?.status == DownloadStatus.COMPLETE && download.filePath.isNotBlank()) {
            val mainFile = File(download.filePath)
            if (mainFile.exists()) return Uri.fromFile(mainFile)
        }
        return if (streamUrl.isNotBlank()) Uri.parse(streamUrl) else null
    }

    /** Is any audio for this talk stored offline? (Then the stored version is what plays.) */
    fun hasOfflineAudio(catNum: String, download: DownloadEntity?): Boolean =
        File(DownloadWorker.trackFilePath(context, catNum, 0)).exists() ||
            (download?.status == DownloadStatus.COMPLETE && download.filePath.isNotBlank() && File(download.filePath).exists())

    /** One MediaItem per chapter (or one for single-track talks), with identifying extras. */
    fun buildMediaItems(talk: Talk, download: DownloadEntity?, useRemaster: Boolean): List<MediaItem> {
        fun item(uri: Uri, index: Int, chapterTitle: String, trackId: String): MediaItem = MediaItem.Builder()
            .setMediaId(MediaIds.chapter(talk.catNum, index))
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(talk.title)
                    .setSubtitle(chapterTitle.takeIf { it.isNotBlank() } ?: "")
                    .setArtist(talk.speaker)
                    .setArtworkUri(if (talk.imageUrl.isNotBlank()) Uri.parse(talk.imageUrl) else null)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setExtras(Bundle().apply {
                        putString(EXTRA_CAT_NUM, talk.catNum)
                        putInt(EXTRA_TRACK_INDEX, index)
                        putString(EXTRA_TRACK_ID, trackId)
                    })
                    .build()
            )
            .build()

        if (talk.tracks.isEmpty()) {
            val uri = resolveTrackUri(talk.catNum, 0, talk.audioUrl, download) ?: return emptyList()
            return listOf(item(uri, 0, "", ""))
        }
        return talk.tracks.mapIndexedNotNull { index, track ->
            val streamUrl = if (useRemaster && track.hasRemaster) track.remasterAudioUrl else track.audioUrl
            resolveTrackUri(talk.catNum, index, streamUrl, download)?.let { item(it, index, track.title, track.trackId) }
        }
    }
}

/** Identity of a queued chapter, read back from its MediaItem. */
data class QueuedChapter(val catNum: String, val trackIndex: Int, val trackId: String)

fun MediaItem.queuedChapter(): QueuedChapter? {
    val extras = mediaMetadata.extras ?: return null
    val catNum = extras.getString(PlaybackResolver.EXTRA_CAT_NUM) ?: return null
    return QueuedChapter(catNum, extras.getInt(PlaybackResolver.EXTRA_TRACK_INDEX, 0), extras.getString(PlaybackResolver.EXTRA_TRACK_ID) ?: "")
}
