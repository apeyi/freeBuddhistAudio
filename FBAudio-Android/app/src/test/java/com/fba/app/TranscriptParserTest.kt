package com.fba.app

import com.fba.app.data.remote.TranscriptParser
import org.junit.Assert.*
import org.junit.Test

class TranscriptParserTest {

    @Test
    fun `contentJsonToPlainText extracts paragraphs`() {
        val html = "<p>First paragraph.</p><p>Second paragraph.</p>"
        val result = TranscriptParser.contentJsonToPlainText(html)
        assertTrue(result.contains("First paragraph."))
        assertTrue(result.contains("Second paragraph."))
    }

    @Test
    fun `contentJsonToPlainText extracts headers`() {
        val html = "<h1>Title</h1><p>Content here.</p>"
        val result = TranscriptParser.contentJsonToPlainText(html)
        assertTrue(result.contains("Title"))
        assertTrue(result.contains("Content here."))
    }

    @Test
    fun `contentJsonToPlainText handles empty input`() {
        assertEquals("", TranscriptParser.contentJsonToPlainText(""))
    }

    @Test
    fun `parseTranscriptHtml falls back to body text`() {
        val html = "<html><body><div class=\"content\">Some transcript text</div></body></html>"
        val result = TranscriptParser.parseTranscriptHtml(html)
        assertTrue(result.contains("Some transcript text"))
    }

    @Test
    fun `parseTranscriptHtml extracts from FBA JSON`() {
        val html = """
            <html><head><script>
            document.__FBA__.text = {"content": "<p>The dharma talk begins.</p><p>And continues here.</p>"};
            </script></head><body></body></html>
        """.trimIndent()
        val result = TranscriptParser.parseTranscriptHtml(html)
        assertTrue(result.contains("The dharma talk begins."))
        assertTrue(result.contains("And continues here."))
    }
}
