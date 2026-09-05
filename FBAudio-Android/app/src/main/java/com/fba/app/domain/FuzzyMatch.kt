package com.fba.app.domain

import java.text.Normalizer

/**
 * Typo-tolerant name matching for speakers, places and collections in search
 * ("adhistana" → Adhisthana, "sanghrakshita" → Sangharakshita). Substring
 * matches always count; otherwise the query may differ from a word of the name
 * (or its prefix) by a small edit distance that scales with the query length.
 */
object FuzzyMatch {

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .trim()

    /** Allowed typos: none for short queries, one from 5 characters, two from 8. */
    fun allowedEdits(queryLength: Int): Int = when {
        queryLength >= 8 -> 2
        queryLength >= 5 -> 1
        else -> 0
    }

    fun matches(query: String, name: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return false
        val n = normalize(name)
        if (n.contains(q)) return true
        val allowed = allowedEdits(q.length)
        if (allowed == 0) return false
        val words = n.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        for (w in words + n) {
            if (distance(q, w) <= allowed) return true
            // Typing only the start of a longer word: compare against its prefix
            if (w.length > q.length) {
                if (distance(q, w.take(q.length)) <= allowed) return true
                if (distance(q, w.take(q.length + 1)) <= allowed) return true
            }
        }
        return false
    }

    /** Damerau–Levenshtein distance (transpositions count as one edit). */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }
}
