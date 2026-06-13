package com.smssentry.deepcheck.session

import com.smssentry.deepcheck.model.VerdictParser
import org.junit.Assert.*
import org.junit.Test

class VerdictParserTest {

    @Test
    fun `extractJson finds JSON in text`() {
        val text = "Here is my analysis:\n{\"verdict\":\"SCAM\",\"confidence\":0.9,\"reasoning\":\"test\",\"evidence\":[\"e1\"]}"
        val json = VerdictParser.extractJson(text)
        assertNotNull(json)
        assertTrue(json!!.contains("SCAM"))
    }

    @Test
    fun `extractJson returns null for no JSON`() {
        val text = "This is plain text with no JSON."
        assertNull(VerdictParser.extractJson(text))
    }

    @Test
    fun `extractJson handles JSON in markdown fences`() {
        val text = "```json\n{\"verdict\":\"SAFE\",\"confidence\":0.95,\"reasoning\":\"ok\",\"evidence\":[]}\n```"
        val json = VerdictParser.extractJson(text)
        assertNotNull(json)
    }

    @Test
    fun `parseVerdict returns valid verdict`() {
        val json = """{"verdict":"SCAM","confidence":0.9,"reasoning":"test","evidence":["e1","e2"]}"""
        val verdict = VerdictParser.parseVerdict(json)
        assertNotNull(verdict)
        assertEquals("SCAM", verdict!!.verdict)
        assertEquals(0.9f, verdict.confidence)
    }

    @Test
    fun `parseVerdict returns null for invalid verdict label`() {
        val json = """{"verdict":"INVALID","confidence":0.9,"reasoning":"test","evidence":[]}"""
        assertNull(VerdictParser.parseVerdict(json))
    }

    @Test
    fun `parseVerdict returns null for out of range confidence`() {
        val json = """{"verdict":"SAFE","confidence":1.5,"reasoning":"test","evidence":[]}"""
        assertNull(VerdictParser.parseVerdict(json))
    }

    @Test
    fun `parseVerdict returns null for malformed JSON`() {
        assertNull(VerdictParser.parseVerdict("not json at all"))
    }

    @Test
    fun `parseVerdict handles all valid verdict labels`() {
        for (label in listOf("SAFE", "SCAM", "SUSPICIOUS")) {
            val json = """{"verdict":"$label","confidence":0.8,"reasoning":"test","evidence":[]}"""
            assertNotNull(VerdictParser.parseVerdict(json))
        }
    }
}
