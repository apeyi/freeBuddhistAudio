package com.fba.app.data.remote

import com.google.gson.JsonParser
import org.jsoup.Jsoup

/**
 * Shared utility for parsing transcript HTML into plain text.
 * Used by both FBAScraper (live fetch) and DownloadWorker (offline save).
 */
object TranscriptParser {

    /**
     * Extract plain text from a full transcript page HTML.
     * Tries document.__FBA__.text.content JSON first, falls back to CSS selectors.
     */
    fun parseTranscriptHtml(html: String): String {
        val textJson = extractFbaTextJson(html)
        if (textJson != null) {
            val text = contentJsonToPlainText(textJson)
            if (text.isNotBlank()) return text
        }
        // Fallback: extract body text from the HTML page
        val doc = Jsoup.parse(html)
        return doc.select(".text-content, .content, article, main").text().ifBlank {
            doc.body().text()
        }
    }

    /**
     * Extract the JSON string value of document.__FBA__.text.content from HTML.
     */
    private fun extractFbaTextJson(html: String): String? {
        val doc = Jsoup.parse(html)
        for (script in doc.select("script")) {
            val data = script.data()
            val marker = "document.__FBA__.text"
            val idx = data.indexOf(marker)
            if (idx >= 0) {
                val braceIdx = data.indexOf('{', idx)
                if (braceIdx >= 0) {
                    val jsonStr = extractBalancedBraces(data, braceIdx) ?: continue
                    return try {
                        val json = JsonParser.parseString(jsonStr).asJsonObject
                        json.get("content")?.asString
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        return null
    }

    /**
     * Convert HTML content string to plain text with paragraph breaks.
     */
    fun contentJsonToPlainText(content: String): String {
        if (content.isBlank()) return ""
        val doc = Jsoup.parse(content)
        doc.outputSettings().prettyPrint(false)
        val sb = StringBuilder()
        for (el in doc.select("p, br, h1, h2, h3, h4, h5, h6, blockquote, li")) {
            val text = el.text().trim()
            if (text.isNotBlank()) {
                sb.append(text).append("\n\n")
            }
        }
        if (sb.isNotBlank()) return sb.toString().trim()
        return doc.wholeText().trim()
    }

    private fun extractBalancedBraces(data: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until data.length) {
            val c = data[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') { depth--; if (depth == 0) return data.substring(start, i + 1) }
            }
        }
        return null
    }
}
