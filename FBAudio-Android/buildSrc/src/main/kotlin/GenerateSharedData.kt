import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import com.google.gson.Gson
import java.io.File

open class GenerateSharedData : DefaultTask() {

    @InputDirectory
    var sharedDataDir: File = project.file("../../fbaudio-shared")

    @OutputDirectory
    var outputDir: File = project.layout.buildDirectory.dir("generated/shared").get().asFile

    @TaskAction
    fun generate() {
        val gson = Gson()
        val outPkg = File(outputDir, "com/fba/app/domain/model")
        outPkg.mkdirs()

        generateSangharakshita(gson, outPkg)
        copyImages()
    }

    private fun copyImages() {
        val imagesDir = File(sharedDataDir, "images")
        if (!imagesDir.exists()) return
        val outRes = File(outputDir, "../res/drawable".replace("../", "")).let {
            // Put generated resources in a separate directory
            File(project.layout.buildDirectory.get().asFile, "generated/shared-res/drawable")
        }
        outRes.mkdirs()
        imagesDir.listFiles()?.forEach { file ->
            file.copyTo(File(outRes, file.name), overwrite = true)
        }
    }

    private fun generateSangharakshita(gson: Gson, outDir: File) {
        val json = File(sharedDataDir, "sangharakshita.json").readText()
        val data = gson.fromJson<SangData>(json, SangData::class.java)

        val entriesLiteral = data.talks.joinToString(",\n            ") { t ->
            "Entry(\"${esc(t.catNum)}\", \"${esc(t.title)}\", ${t.year}, \"${esc(t.imageUrl)}\")"
        }

        val seriesLiteral = data.series.joinToString(",\n            ") { s ->
            "Series(\"${esc(s.id)}\", \"${esc(s.title)}\")"
        }

        File(outDir, "SangharakshitaData.kt").writeText("""
package com.dharmachakra.fba_android.domain.model

object SangharakshitaData {

    private data class Entry(val catNum: String, val title: String, val year: Int, val imageUrl: String)

    private val entries: List<Entry> = listOf(
            $entriesLiteral
    )

    /** Fix titles where 'The/A/An' has been moved to the end for sorting. */
    private fun fixTitle(title: String): String = when {
        title.endsWith(", The") -> "The " + title.dropLast(5)
        title.endsWith(", A") -> "A " + title.dropLast(3)
        title.endsWith(", An") -> "An " + title.dropLast(4)
        else -> title
    }

    fun allTalksAsSearchResults(): List<SearchResult> = entries.map { e ->
        SearchResult(
            catNum = e.catNum,
            title = fixTitle(e.title),
            speaker = "Sangharakshita",
            imageUrl = e.imageUrl,
            path = "https://www.freebuddhistaudio.com/audio/details?num=${'$'}{e.catNum}",
            year = e.year,
        )
    }

    data class Series(val id: String, val title: String)

    val series: List<Series> = listOf(
            $seriesLiteral
    )

    fun seriesAsBrowseCategories(): List<BrowseCategory> = series.map { s ->
        BrowseCategory(
            id = "sang_series_${'$'}{s.id}",
            name = s.title,
            type = CategoryType.SERIES,
            browseUrl = "https://www.freebuddhistaudio.com/series/details?num=${'$'}{s.id}",
        )
    }
}
""".trimStart())
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\${'$'}")

    // JSON model classes
    private data class SangData(val speaker: String, val talks: List<SangTalk>, val series: List<SangSeries>)
    private data class SangTalk(val catNum: String, val title: String, val year: Int, val imageUrl: String)
    private data class SangSeries(val id: String, val title: String)
}
