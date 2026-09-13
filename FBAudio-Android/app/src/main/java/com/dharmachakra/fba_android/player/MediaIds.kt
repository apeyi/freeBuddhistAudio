package com.dharmachakra.fba_android.player

/**
 * Media ids for the browse tree exposed to Android Auto (and any other
 * MediaBrowser). Pure string scheme, unit-tested: the service and the player
 * both need to agree on it.
 *
 *  root                     the tree root
 *  recent | downloads | sangharakshita | latest      top-level folders (Auto tabs)
 *  series/<id>              a Sangharakshita series (folder of talks)
 *  talk/<catNum>            a talk — playable; expands to all its chapters
 *  talk/<catNum>/<index>    one chapter of a talk (the items actually queued)
 */
object MediaIds {
    const val ROOT = "root"
    const val RECENT = "recent"
    const val DOWNLOADS = "downloads"
    const val SANGHARAKSHITA = "sangharakshita"
    const val LATEST = "latest"

    val FOLDERS = listOf(RECENT, DOWNLOADS, SANGHARAKSHITA, LATEST)

    fun talk(catNum: String) = "talk/$catNum"
    fun chapter(catNum: String, trackIndex: Int) = "talk/$catNum/$trackIndex"
    fun series(id: String) = "series/$id"

    sealed class Parsed {
        object Root : Parsed()
        data class Folder(val key: String) : Parsed()
        data class Series(val id: String) : Parsed()
        /** [trackIndex] is null for a whole talk, set for one chapter. */
        data class Talk(val catNum: String, val trackIndex: Int? = null) : Parsed()
    }

    fun parse(mediaId: String): Parsed? {
        if (mediaId == ROOT) return Parsed.Root
        if (mediaId in FOLDERS) return Parsed.Folder(mediaId)
        val parts = mediaId.split('/')
        return when {
            parts.size == 2 && parts[0] == "series" && parts[1].isNotBlank() -> Parsed.Series(parts[1])
            parts.size == 2 && parts[0] == "talk" && parts[1].isNotBlank() -> Parsed.Talk(parts[1])
            parts.size == 3 && parts[0] == "talk" && parts[1].isNotBlank() ->
                parts[2].toIntOrNull()?.takeIf { it >= 0 }?.let { Parsed.Talk(parts[1], it) }
            else -> null
        }
    }
}
