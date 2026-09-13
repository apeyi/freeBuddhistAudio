package com.dharmachakra.fba_android.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dharmachakra.fba_android.data.auth.AuthRepository
import com.dharmachakra.fba_android.data.local.RecentlyListenedDao
import com.dharmachakra.fba_android.data.local.RecentlyListenedEntity
import com.dharmachakra.fba_android.data.repository.HistoryRepository
import com.dharmachakra.fba_android.data.repository.TalkRepository
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.ui.player.PlaybackMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records what's being listened to, from the service's player — so it works
 * whether playback was started from the app, the notification or Android Auto
 * (where the UI process may not exist at all):
 *
 *  - resume position per talk + last talk (SharedPreferences, sync commit)
 *  - Recently listened (Room)
 *  - the FBA account: a stream-start when a talk begins, and the resume
 *    "checkpoint" every 10 s while playing and on pause
 *
 * Runs every 5 s while playing, on pause/stop, and on chapter changes; clears
 * the resume point when a talk finishes.
 */
@Singleton
class PlaybackPersistence @Inject constructor(
    @ApplicationContext context: Context,
    private val talkRepository: TalkRepository,
    private val recentlyListenedDao: RecentlyListenedDao,
    private val history: HistoryRepository,
    private val auth: AuthRepository,
    private val appScope: CoroutineScope,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PlaybackResolver.PREFS, Context.MODE_PRIVATE)
    private val talkMemo = HashMap<String, Talk>()
    private var streamStartRecordedFor: String? = null
    private var lastCheckpointTime = 0L
    private var ticker: Job? = null

    /** Attach to the service's player. [scope] must be a main-thread scope (Player is main-thread only). */
    fun attach(player: Player, scope: CoroutineScope) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    recordStreamStartIfNew(player)
                    ticker?.cancel()
                    ticker = scope.launch {
                        while (isActive) {
                            delay(5_000)
                            save(player, force = false)
                        }
                    }
                } else {
                    ticker?.cancel()
                    ticker = null
                    save(player, force = true)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // New chapter (auto-advance or seek): note it right away so a kill
                // mid-chapter resumes at the right one.
                save(player, force = false)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.currentMediaItem?.queuedChapter()?.let { clearResumePoint(it.catNum) }
                }
            }
        })
    }

    private fun recordStreamStartIfNew(player: Player) {
        val catNum = player.currentMediaItem?.queuedChapter()?.catNum ?: return
        if (streamStartRecordedFor == catNum) return
        streamStartRecordedFor = catNum
        // Mirror the website's history so web and app listening stay in step.
        appScope.launch { history.recordStreamStart(catNum) }
    }

    /** Save the current position. [force] also posts the checkpoint regardless of throttling (pause/stop). */
    fun save(player: Player, force: Boolean) {
        val item = player.currentMediaItem ?: return
        val chapter = item.queuedChapter() ?: return
        val pos = player.currentPosition.coerceAtLeast(0)
        val dur = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
        if (pos <= 0 && dur <= 0) return // don't overwrite good data with zeros

        prefs.edit()
            .putString("last_cat_num", chapter.catNum)
            .putLong("last_position_${chapter.catNum}", pos)
            .putInt("last_track_index_${chapter.catNum}", chapter.trackIndex)
            .putLong("last_duration_${chapter.catNum}", dur)
            .commit() // sync write — survives process death

        val now = System.currentTimeMillis()
        if (auth.isLoggedIn && (force || now - lastCheckpointTime > 10_000)) {
            lastCheckpointTime = now
            appScope.launch { history.postCheckpoint(chapter.catNum, chapter.trackId, (pos / 1000).toInt()) }
        }

        val title = item.mediaMetadata.title?.toString() ?: ""
        val speaker = item.mediaMetadata.artist?.toString() ?: ""
        val imageUrl = item.mediaMetadata.artworkUri?.toString() ?: ""
        appScope.launch {
            // Cumulative position across chapters needs the talk's track lengths
            val talk = talkMemo[chapter.catNum] ?: talkRepository.getTalkDetail(chapter.catNum)?.also { talkMemo[chapter.catNum] = it }
            val tracks = talk?.tracks ?: emptyList()
            recentlyListenedDao.upsert(
                RecentlyListenedEntity(
                    catNum = chapter.catNum,
                    title = talk?.title?.ifBlank { title } ?: title,
                    speaker = talk?.speaker?.ifBlank { speaker } ?: speaker,
                    imageUrl = talk?.imageUrl?.ifBlank { imageUrl } ?: imageUrl,
                    positionMs = PlaybackMath.cumulativePositionMs(tracks, chapter.trackIndex, pos),
                    durationMs = dur,
                    trackIndex = chapter.trackIndex,
                    totalDurationSeconds = PlaybackMath.totalDurationSeconds(talk?.durationSeconds ?: 0, tracks, dur),
                )
            )
            recentlyListenedDao.pruneOld()
        }
    }

    /** A finished talk replays from the start instead of "resuming" its last seconds. */
    private fun clearResumePoint(catNum: String) {
        prefs.edit()
            .remove("last_position_$catNum")
            .remove("last_track_index_$catNum")
            .commit()
    }
}
