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
        generateMitraStudy(gson, outPkg)
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
package com.fba.app.domain.model

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

    private fun generateMitraStudy(gson: Gson, outDir: File) {
        val json = File(sharedDataDir, "mitra_study.json").readText()
        val data = gson.fromJson<MitraData>(json, MitraData::class.java)

        val modulesLiteral = data.modules.joinToString(",\n        ") { m ->
            val seriesCodes = if (m.seriesCodes.isNotEmpty()) {
                "seriesCodes = listOf(${m.seriesCodes.joinToString(", ") { "\"${esc(it)}\"" }}),\n            "
            } else ""
            val talks = m.talks.joinToString(",\n                ") { t ->
                "MitraTalk(\"${esc(t.catNum)}\", \"${esc(t.title)}\", \"${esc(t.speaker)}\", \"${esc(t.imageUrl)}\")"
            }
            """MitraModule(
            id = "${esc(m.id)}", name = "${esc(m.name)}", year = ${m.year},
            ${seriesCodes}talks = listOf(
                $talks
            ),
        )"""
        }

        File(outDir, "MitraStudyData.kt").writeText("""
package com.fba.app.domain.model

data class MitraModule(
    val id: String,
    val name: String,
    val year: Int,
    val talks: List<MitraTalk>,
    val seriesCodes: List<String> = emptyList(),
)

data class MitraTalk(
    val catNum: String,
    val title: String,
    val speaker: String,
    val imageUrl: String = "",
)

object MitraStudyData {

    val modules: List<MitraModule> = listOf(
        $modulesLiteral
    )

    /** Get modules grouped by year. */
    fun modulesByYear(): Map<Int, List<MitraModule>> = modules.groupBy { it.year }

    /** Convert module talks to SearchResult list for display in browse screen. */
    fun moduleTalksAsSearchResults(moduleId: String): List<SearchResult> {
        val module = modules.find { it.id == moduleId } ?: return emptyList()
        return module.talks.map { talk ->
            SearchResult(
                catNum = talk.catNum,
                title = talk.title,
                speaker = talk.speaker,
                imageUrl = talk.imageUrl,
                path = "https://www.freebuddhistaudio.com/audio/details?num=${'$'}{talk.catNum}",
            )
        }
    }

    /** Get year sub-categories as BrowseCategory list. */
    fun yearCategories(): List<BrowseCategory> {
        return (1..4).map { year ->
            BrowseCategory(
                id = "mitra_year_${'$'}year",
                name = "Year ${'$'}year",
                type = CategoryType.MITRA_YEAR,
                browseUrl = "mitra://year/${'$'}year",
            )
        }
    }

    /** Get module sub-categories for a given year. */
    fun moduleCategories(year: Int): List<BrowseCategory> {
        return modules.filter { it.year == year }.map { module ->
            BrowseCategory(
                id = module.id,
                name = module.name,
                type = CategoryType.MITRA_MODULE,
                browseUrl = "mitra://module/${'$'}{module.id}",
            )
        }
    }
}
""".trimStart())
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\${'$'}")

    // JSON model classes
    private data class SangData(val speaker: String, val talks: List<SangTalk>, val series: List<SangSeries>)
    private data class SangTalk(val catNum: String, val title: String, val year: Int, val imageUrl: String)
    private data class SangSeries(val id: String, val title: String)
    private data class MitraData(val modules: List<MitraModuleJson>)
    private data class MitraModuleJson(val id: String, val name: String, val year: Int, val seriesCodes: List<String>, val talks: List<MitraTalkJson>)
    private data class MitraTalkJson(val catNum: String, val title: String, val speaker: String, val imageUrl: String)
}
