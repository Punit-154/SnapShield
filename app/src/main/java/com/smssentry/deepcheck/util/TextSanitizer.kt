package com.smssentry.deepcheck.util

/**
 * Converts raw LLM output (which may contain markdown formatting,
 * bullet points, numbered lists, headers, etc.) into clean flowing
 * paragraph text suitable for display in a plain Text() composable.
 *
 * On-device models like Gemma 4B consistently produce markdown-style
 * output regardless of system prompt instructions. This post-processor
 * is the only reliable way to guarantee paragraph format.
 */
object TextSanitizer {

    /**
     * Strip all markdown formatting from [raw] and return clean
     * paragraph text with natural sentence flow.
     */
    fun toParagraph(raw: String): String {
        if (raw.isBlank()) return ""

        val lines = raw.lines()
        val cleaned = StringBuilder()
        var prevWasBlank = false

        for (line in lines) {
            var l = line

            // Strip markdown headers (### Header → Header)
            l = l.replace(Regex("""^#{1,6}\s*"""), "")

            // Strip bold markers (**text** → text, __text__ → text)
            l = l.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            l = l.replace(Regex("""__(.+?)__"""), "$1")

            // Strip italic markers (*text* → text, _text_ → text)
            // Be careful not to strip mid-word underscores
            l = l.replace(Regex("""\*([^*]+)\*"""), "$1")
            l = l.replace(Regex("""(?<=\s|^)_([^_]+)_(?=\s|$)"""), "$1")

            // Strip inline code (`code` → code)
            l = l.replace(Regex("""`([^`]+)`"""), "$1")

            // Strip unordered list markers (-, *, •, ▪, ◦ at start of line)
            l = l.replace(Regex("""^\s*[-*•▪◦]\s+"""), "")

            // Strip ordered list markers (1., 2., etc.)
            l = l.replace(Regex("""^\s*\d+[.)]\s+"""), "")

            // Strip blockquote markers (> at start of line)
            l = l.replace(Regex("""^\s*>\s*"""), "")

            // Strip horizontal rules (---, ***, ___)
            if (l.trim().matches(Regex("""^[-*_]{3,}$"""))) {
                l = ""
            }

            // Collapse remaining whitespace
            l = l.trim()

            if (l.isEmpty()) {
                // Blank line = paragraph break, but avoid consecutive blanks
                if (!prevWasBlank && cleaned.isNotEmpty()) {
                    cleaned.append("\n\n")
                    prevWasBlank = true
                }
            } else {
                if (prevWasBlank) {
                    prevWasBlank = false
                } else if (cleaned.isNotEmpty() && !cleaned.endsWith("\n")) {
                    // Join consecutive non-blank lines with a space
                    // to create flowing paragraph text
                    cleaned.append(" ")
                }
                cleaned.append(l)
                prevWasBlank = false
            }
        }

        return cleaned.toString().trim()
    }

    /**
     * Create a short summary (first [maxLen] characters) from the
     * sanitized text, breaking at a sentence boundary when possible.
     */
    fun summarize(raw: String, maxLen: Int = 200): String {
        val clean = toParagraph(raw)
        if (clean.length <= maxLen) return clean

        // Try to break at a sentence boundary
        val truncated = clean.take(maxLen)
        val lastPeriod = truncated.lastIndexOf('.')
        val lastQuestion = truncated.lastIndexOf('?')
        val lastExclaim = truncated.lastIndexOf('!')
        val breakPoint = maxOf(lastPeriod, lastQuestion, lastExclaim)

        return if (breakPoint > maxLen / 2) {
            truncated.substring(0, breakPoint + 1)
        } else {
            // No good sentence boundary — break at last space
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > maxLen / 2) {
                truncated.substring(0, lastSpace) + "…"
            } else {
                truncated + "…"
            }
        }
    }
}
