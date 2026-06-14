package com.smssentry.deepcheck.util

import org.junit.Assert.*
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun `toParagraph strips markdown bullets`() {
        val input = """
            - This is a bullet point
            - This is another bullet
            * And a star bullet
            • Unicode bullet too
        """.trimIndent()

        val result = TextSanitizer.toParagraph(input)
        assertFalse("Should not contain dash bullets", result.contains(Regex("""^\s*-\s""")))
        assertFalse("Should not contain star bullets", result.contains(Regex("""^\s*\*\s""")))
        assertFalse("Should not contain unicode bullets", result.contains("•"))
        assertTrue("Should contain all text", result.contains("This is a bullet point"))
        assertTrue("Should flow as paragraph", result.contains("bullet point This is another"))
    }

    @Test
    fun `toParagraph strips numbered lists`() {
        val input = """
            1. First item
            2. Second item
            3) Third item
        """.trimIndent()

        val result = TextSanitizer.toParagraph(input)
        assertFalse("Should not contain list numbers", result.contains(Regex("""^\d+[.)]\s""")))
        assertTrue("Should contain text", result.contains("First item"))
    }

    @Test
    fun `toParagraph strips markdown headers`() {
        val input = """
            ### Red Flags
            This message has problems.
            ## Summary
            It is a scam.
        """.trimIndent()

        val result = TextSanitizer.toParagraph(input)
        assertFalse("Should not contain hash headers", result.contains("#"))
        assertTrue("Should contain header text", result.contains("Red Flags"))
        assertTrue("Should contain body text", result.contains("This message has problems"))
    }

    @Test
    fun `toParagraph strips bold and italic markers`() {
        val input = "This is **very important** and *also italic* text."
        val result = TextSanitizer.toParagraph(input)
        assertFalse("Should not contain stars", result.contains("**"))
        assertFalse("Should not contain single star", result.matches(Regex(""".*\*\w.*""")))
        assertTrue("Should have clean text", result.contains("very important"))
        assertTrue("Should have clean text", result.contains("also italic"))
    }

    @Test
    fun `toParagraph preserves paragraph breaks`() {
        val input = """
            First paragraph here.

            Second paragraph here.
        """.trimIndent()

        val result = TextSanitizer.toParagraph(input)
        assertTrue("Should preserve paragraph break", result.contains("\n\n"))
        assertTrue("Should have both paragraphs", result.contains("First paragraph"))
        assertTrue("Should have both paragraphs", result.contains("Second paragraph"))
    }

    @Test
    fun `toParagraph joins consecutive lines into flowing text`() {
        val input = """
            This is the first line.
            This is the second line.
            This is the third line.
        """.trimIndent()

        val result = TextSanitizer.toParagraph(input)
        assertFalse("Should not have inner newlines", result.contains("\n"))
        assertTrue("Lines should be joined", result.contains("first line. This is the second"))
    }

    @Test
    fun `toParagraph handles real model output with mixed formatting`() {
        val input = """
            ### Analysis

            **Red Flags Detected:**

            - The URL `secure-bank.xyz` is not the official bank website
            - The message creates **urgency** by threatening account suspension
            - The sender is an unknown number, not the bank's official SMS channel

            1. Do not click the link
            2. Contact your bank directly
            3. Report this message as spam

            > This is a classic phishing attempt.
        """.trimIndent()

        val result = TextSanitizer.toParagraph(input)
        assertFalse("No hash headers", result.contains("#"))
        assertFalse("No bold markers", result.contains("**"))
        assertFalse("No dash bullets", result.startsWith("-"))
        assertFalse("No blockquote markers", result.contains("> "))
        assertTrue("Has flowing text", result.contains("The URL"))
        assertTrue("Has flowing text", result.contains("classic phishing attempt"))
    }

    @Test
    fun `toParagraph handles blank input`() {
        assertEquals("", TextSanitizer.toParagraph(""))
        assertEquals("", TextSanitizer.toParagraph("   "))
    }

    @Test
    fun `summarize breaks at sentence boundary`() {
        val input = "This is the first sentence. This is the second sentence. This is the third sentence which is very long."
        val result = TextSanitizer.summarize(input, maxLen = 60)
        assertTrue("Should end at sentence boundary", result.endsWith("."))
        assertTrue("Should contain first sentence", result.contains("first sentence"))
    }

    @Test
    fun `summarize falls back to space break when no sentence boundary`() {
        val input = "This is a very long sentence without any periods that goes on and on and on for a really long time"
        val result = TextSanitizer.summarize(input, maxLen = 50)
        assertTrue("Should end with ellipsis", result.endsWith("…"))
        assertFalse("Should not cut mid-word", result.endsWith("a…"))
    }
}
