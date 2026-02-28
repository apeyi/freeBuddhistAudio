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
)

object MitraStudyData {

    val modules: List<MitraModule> = listOf(
        // ── Year 1 ──────────────────────────────────────────────────
        MitraModule(
            id = "y1_refuge", name = "Going for Refuge", year = 1,
            seriesCodes = listOf("X04"),
            talks = listOf(
                MitraTalk("09", "Going for Refuge", "Sangharakshita"),
                MitraTalk("137", "Levels of Going for Refuge", "Sangharakshita"),
                MitraTalk("154", "Dimensions of Going for Refuge", "Sangharakshita"),
                MitraTalk("171", "The History of My Going for Refuge", "Sangharakshita"),
                MitraTalk("08", "Introducing Buddhism", "Sangharakshita"),
                MitraTalk("19", "The Approach to Buddhism", "Sangharakshita"),
                MitraTalk("139", "The Taste of Freedom", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y1_ethics", name = "Ethics", year = 1,
            talks = listOf(
                MitraTalk("161", "The Ten Pillars of Buddhism", "Sangharakshita"),
                MitraTalk("LOC3546", "The Ten Pillars of Buddhism Part 1", "Padmavajra"),
            ),
        ),
        MitraModule(
            id = "y1_meditation", name = "Meditation", year = 1,
            talks = listOf(
                MitraTalk("135", "A System of Meditation", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y1_wisdom", name = "Wisdom", year = 1,
            talks = listOf(
                MitraTalk("24", "The Dynamics of Being", "Sangharakshita"),
                MitraTalk("31", "Mind: Reactive and Creative", "Sangharakshita"),
                MitraTalk("103", "The Symbolism of the Tibetan Wheel of Life", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y1_triratna", name = "Buddhism and Triratna", year = 1,
            talks = listOf(
                MitraTalk("LOC517", "Triratna Buddhism", "Vadanya"),
                MitraTalk("36", "The Psychology of Buddhist Ritual", "Sangharakshita"),
            ),
        ),

        // ── Year 2 ──────────────────────────────────────────────────
        MitraModule(
            id = "y2_eightfold", name = "Noble Eightfold Path", year = 2,
            seriesCodes = listOf("X07"),
            talks = listOf(
                MitraTalk("185", "The Transcendental Eightfold Path", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y2_conditionality", name = "Pratitya-Samutpada", year = 2,
            talks = listOf(
                MitraTalk("24", "The Dynamics of Being", "Sangharakshita"),
                MitraTalk("LOC432", "Revering and Relying Upon the Dharma", "Subhuti"),
                MitraTalk("LOC217", "Twenty Four Nidana Reflection", "Dhivan"),
            ),
        ),
        MitraModule(
            id = "y2_five_aspects", name = "Five Aspects of Dharma Life", year = 2,
            seriesCodes = listOf("X74"),
            talks = listOf(
                MitraTalk("LOC1691", "The Five Aspects of Dharma Life - Integration", "Subhuti"),
                MitraTalk("LOC1698", "The Five Aspects of Dharma Life - Positive Emotion", "Subhuti"),
                MitraTalk("LOC1699", "The Five Aspects of Dharma Life - Questions and Answers", "Subhuti"),
                MitraTalk("LOC1701", "The Five Aspects of Dharma Life - Spiritual Receptivity", "Subhuti"),
                MitraTalk("LOC1714", "The Five Aspects of Dharma Life - Spiritual Rebirth", "Subhuti"),
                MitraTalk("LOC1715", "The Five Aspects of Dharma Life - Applying the Five Aspects in Daily Life", "Subhuti"),
            ),
        ),
        MitraModule(
            id = "y2_mind_turning", name = "Turning the Mind", year = 2,
            seriesCodes = listOf("X25"),
            talks = listOf(
                MitraTalk("OM739", "The Four Mind-Turning Reflections", "Dhammadinna"),
                MitraTalk("OM743", "The Defects and Dangers of Samsara", "Maitreyi"),
                MitraTalk("LOC51", "Maturing the Mind - Introduction to the Four Reminders", "Kulaprabha"),
            ),
        ),
        MitraModule(
            id = "y2_mindfulness", name = "Way of Mindfulness", year = 2,
            seriesCodes = listOf("X93"),
            talks = listOf(
                MitraTalk("OM838", "The Way of Mindfulness - Introduction to the Satipatthana Sutta", "Vidyamala"),
                MitraTalk("M14", "The Way of Mindfulness Week 1 - Awareness Meditation", "Vidyamala"),
                MitraTalk("M15", "The Way of Mindfulness Week 2 - Body Scan (Breath-Based)", "Vidyamala"),
                MitraTalk("M16", "The Way of Mindfulness Week 3 - Vedana Meditation", "Vidyamala"),
                MitraTalk("M17", "The Way of Mindfulness Week 4 - Unworldly Vedana Meditation", "Vidyamala"),
                MitraTalk("M18", "The Way of Mindfulness Week 5 - Citta Meditation", "Vidyamala"),
                MitraTalk("M19", "The Way of Mindfulness Week 6 - Working with Thoughts and Emotions", "Vidyamala"),
                MitraTalk("M20", "The Way of Mindfulness Week 7 - Hindrances and Awakening Factors", "Vidyamala"),
                MitraTalk("LOC1016", "Living with Awareness (A Guide to the Satipatthana Sutta) - Part 1", "Sangharakshita"),
                MitraTalk("LOC1017", "Living with Awareness (A Guide to the Satipatthana Sutta) - Part 2", "Sangharakshita"),
                MitraTalk("LOC1018", "Living with Awareness (A Guide to the Satipatthana Sutta) - Part 4", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y2_sangha", name = "What is the Sangha?", year = 2,
            talks = listOf(
                MitraTalk("142", "Commitment and Spiritual Community", "Sangharakshita"),
                MitraTalk("LOC2121", "Year of Spiritual Community Talks", "Various"),
            ),
        ),
        MitraModule(
            id = "y2_tradition", name = "A Living Tradition", year = 2,
            seriesCodes = listOf("X100"),
            talks = listOf(
                MitraTalk("LOC517", "Triratna Buddhism", "Vadanya"),
            ),
        ),

        // ── Year 3 ──────────────────────────────────────────────────
        MitraModule(
            id = "y3_pali", name = "Selected Suttas (Pali Canon)", year = 3,
            talks = listOf(
                MitraTalk("S01", "Readings from the Pali Canon", "Sangharakshita"),
                MitraTalk("LOC45", "The Early Teachings of the Buddha", "Ratnaguna"),
            ),
        ),
        MitraModule(
            id = "y3_dhammapada", name = "The Dhammapada", year = 3,
            seriesCodes = listOf("X37"),
            talks = listOf(
                MitraTalk("OM790", "The Essential Revolution - Dhammapada Verses 1 & 2", "Padmavajra"),
                MitraTalk("OM791", "Changing Hatred into Love - Dhammapada Verses 3 to 6", "Padmavajra"),
                MitraTalk("OM792", "Mindfulness is the Way to the Deathless - Dhammapada Verses 7, 8, 21 & 23", "Padmavajra"),
                MitraTalk("OM793", "Seeing with Insight - Dhammapada Verses 277 to 279", "Padmavajra"),
                MitraTalk("OM794", "Flowers - Dhammapada Verses 44 to 59", "Padmavajra"),
            ),
        ),
        MitraModule(
            id = "y3_insight", name = "Towards Insight", year = 3,
            talks = listOf(
                MitraTalk("LOC100", "Towards Insight - Contemplations of the Buddha", "Dayanandi and Ratnaguna"),
                MitraTalk("LOC101", "Towards Insight - Contemplation of Impermanence", "Dayanandi and Ratnaguna"),
            ),
        ),
        MitraModule(
            id = "y3_mahayana", name = "Mahayana Perspectives", year = 3,
            talks = listOf(
                MitraTalk("143", "The Magic of a Mahayana Sutra", "Sangharakshita"),
            ),
        ),

        // ── Year 4 ──────────────────────────────────────────────────
        MitraModule(
            id = "y4_bodhisattva", name = "The Bodhisattva Ideal", year = 4,
            seriesCodes = listOf("X09"),
            talks = listOf(
                MitraTalk("65", "The Origin and Development of the Bodhisattva Ideal", "Sangharakshita"),
                MitraTalk("67", "The Bodhisattva Vow", "Sangharakshita"),
                MitraTalk("71", "The Bodhisattva Hierarchy", "Sangharakshita"),
                MitraTalk("LOC1020", "The Bodhisattva Ideal: Wisdom and Compassion in Buddhism - Part 1", "Sangharakshita"),
                MitraTalk("LOC3419", "The Bodhisattva Ideal - Talk 6", "Subhuti"),
            ),
        ),
        MitraModule(
            id = "y4_sangha", name = "Nature of the Sangha", year = 4,
            talks = listOf(
                MitraTalk("142", "Commitment and Spiritual Community", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y4_symbols", name = "Faith, Symbols, and Imagination", year = 4,
            seriesCodes = listOf("X13"),
            talks = listOf(
                MitraTalk("103", "The Symbolism of the Tibetan Wheel of Life", "Sangharakshita"),
                MitraTalk("104", "The Tantric Symbolism of the Stupa", "Sangharakshita"),
                MitraTalk("45", "The Mandala: Tantric Symbol of Integration", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y4_ethics_env", name = "Ethics and Environment", year = 4,
            seriesCodes = listOf("X141"),
            talks = emptyList(),
        ),
        MitraModule(
            id = "y4_psychology", name = "Buddhist Psychology", year = 4,
            talks = listOf(
                MitraTalk("40", "The Analytical Psychology of the Abhidharma", "Sangharakshita"),
                MitraTalk("41", "The Psychology of Spiritual Development", "Sangharakshita"),
                MitraTalk("31", "Mind: Reactive and Creative", "Sangharakshita"),
            ),
        ),
        MitraModule(
            id = "y4_history", name = "History of Triratna", year = 4,
            seriesCodes = listOf("X100"),
            talks = listOf(
                MitraTalk("LOC517", "Triratna Buddhism", "Vadanya"),
            ),
        ),
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
                imageUrl = "",
                path = "https://www.freebuddhistaudio.com/audio/details?num=${talk.catNum}",
            )
        }
    }

    /** Get year sub-categories as BrowseCategory list. */
    fun yearCategories(): List<BrowseCategory> {
        return (1..4).map { year ->
            BrowseCategory(
                id = "mitra_year_$year",
                name = "Year $year",
                type = CategoryType.MITRA_YEAR,
                browseUrl = "mitra://year/$year",
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
                browseUrl = "mitra://module/${module.id}",
            )
        }
    }
}
