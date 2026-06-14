package com.smssentry.ml

class BertTokenizer(vocabText: String) {

    private val vocab: Map<String, Int> = buildMap {
        vocabText.lines().forEachIndexed { index, line ->
            val token = line.trim()
            if (token.isNotEmpty()) put(token, index)
        }
    }

    private val unkId = vocab["[UNK]"] ?: 100
    private val clsId = vocab["[CLS]"] ?: 101
    private val sepId = vocab["[SEP]"] ?: 102
    private val padId = 0

    fun tokenize(text: String, maxLength: Int = 128): Pair<LongArray, LongArray> {
        val tokens = mutableListOf<Int>()
        tokens.add(clsId)

        // Normalize: lowercase + basic whitespace clean
        val normalized = text.lowercase().trim()

        // Split into basic tokens (handles punctuation separation)
        val basicTokens = basicTokenize(normalized)

        for (token in basicTokens) {
            if (tokens.size >= maxLength - 1) break
            val pieces = wordPiece(token)
            for (piece in pieces) {
                if (tokens.size >= maxLength - 1) break
                tokens.add(vocab[piece] ?: unkId)
            }
        }

        tokens.add(sepId)

        val inputIds = LongArray(maxLength) { i ->
            if (i < tokens.size) tokens[i].toLong() else padId.toLong()
        }
        val attentionMask = LongArray(maxLength) { i ->
            if (i < tokens.size) 1L else 0L
        }

        return inputIds to attentionMask
    }

    private fun basicTokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()

        for (ch in text) {
            when {
                // Punctuation and special chars get split into their own tokens
                isPunctuation(ch) || isWhitespace(ch) -> {
                    if (sb.isNotEmpty()) {
                        result.add(sb.toString())
                        sb.clear()
                    }
                    if (isPunctuation(ch)) result.add(ch.toString())
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result.filter { it.isNotBlank() }
    }

    private fun wordPiece(word: String): List<String> {
        // If whole word is in vocab, return it directly
        if (word in vocab) return listOf(word)

        val subTokens = mutableListOf<String>()
        var start = 0
        var isBad = false

        while (start < word.length) {
            var end = word.length
            var curSubStr: String? = null

            while (start < end) {
                val subStr = if (start == 0) {
                    word.substring(start, end)
                } else {
                    "##" + word.substring(start, end)
                }

                if (subStr in vocab) {
                    curSubStr = subStr
                    break
                }
                end--
            }

            if (curSubStr == null) {
                isBad = true
                break
            }

            subTokens.add(curSubStr)
            start = end
        }

        return if (isBad) listOf("[UNK]") else subTokens
    }

    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return ch.category == CharCategory.OTHER_PUNCTUATION
    }

    private fun isWhitespace(ch: Char): Boolean {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
    }
}