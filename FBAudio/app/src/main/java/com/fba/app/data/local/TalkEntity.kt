package com.fba.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fba.app.domain.model.Talk
import com.fba.app.domain.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jsoup.parser.Parser

@Entity(tableName = "talks")
data class TalkEntity(
    @PrimaryKey val catNum: String,
    val title: String,
    val speaker: String,
    val year: Int,
    val genre: String,
    val durationSeconds: Int,
    val imageUrl: String,
    val audioUrl: String,
    val description: String,
    val tracksJson: String = "",
    val transcriptUrl: String = "",
    val series: String = "",
    val seriesHref: String = "",
    val cachedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): Talk {
        val trackType = object : TypeToken<List<Track>>() {}.type
        val tracks: List<Track> = if (tracksJson.isNotBlank()) {
            try {
                val raw: List<Track> = Gson().fromJson(tracksJson, trackType)
                raw.map { it.copy(
                    title = Parser.unescapeEntities(it.title, false),
                    durationSeconds = it.durationSeconds.coerceAtLeast(0),
                ) }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
        return Talk(
            catNum = catNum,
            title = Parser.unescapeEntities(title, false),
            speaker = Parser.unescapeEntities(speaker, false),
            year = year,
            genre = genre,
            durationSeconds = durationSeconds.coerceAtLeast(0),
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            description = Parser.unescapeEntities(description, false),
            tracks = tracks,
            transcriptUrl = transcriptUrl,
            series = Parser.unescapeEntities(series, false),
            seriesHref = seriesHref,
        )
    }

    companion object {
        fun fromDomain(talk: Talk) = TalkEntity(
            catNum = talk.catNum,
            title = talk.title,
            speaker = talk.speaker,
            year = talk.year,
            genre = talk.genre,
            durationSeconds = talk.durationSeconds,
            imageUrl = talk.imageUrl,
            audioUrl = talk.audioUrl,
            description = talk.description,
            tracksJson = if (talk.tracks.isNotEmpty()) Gson().toJson(talk.tracks) else "",
            transcriptUrl = talk.transcriptUrl,
            series = talk.series,
            seriesHref = talk.seriesHref,
        )
    }
}
