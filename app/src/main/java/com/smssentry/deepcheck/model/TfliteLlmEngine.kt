package com.smssentry.deepcheck.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfliteLlmEngine(
    private val context: Context,
    private val modelPath: String
) : LlmInferenceEngine {

    private var interpreter: Interpreter? = null
    private var tokenizer: GemmaTokenizer? = null

    private val maxInputLength = 512
    private val maxOutputLength = 256
    private val temperature = 0.7f

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) return@withContext false

            val modelBuffer = loadModelFile(modelFile)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(true)
            }
            interpreter = Interpreter(modelBuffer, options)

            tokenizer = GemmaTokenizer(context)
            tokenizer?.initialize()

            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun generateResponseAsync(
        messages: List<ChatMessage>,
        tools: List<Tool>
    ): LlmResponse? = withContext(Dispatchers.Default) {
        try {
            val interp = interpreter ?: return@withContext null
            val tok = tokenizer ?: return@withContext null

            val prompt = buildPrompt(messages, tools)
            val inputTokens = tok.encode(prompt)

            if (inputTokens.isEmpty()) {
                return@withContext LlmResponse.Error("Failed to tokenize input")
            }

            val inputArray = IntArray(maxInputLength) { i ->
                if (i < inputTokens.size) inputTokens[i] else tok.padTokenId
            }

            val inputBuffer = ByteBuffer.allocateDirect(maxInputLength * 4).apply {
                order(ByteOrder.nativeOrder())
                inputArray.forEach { putInt(it) }
            }

            val outputArray = Array(1) { IntArray(maxOutputLength) }

            interp.run(inputBuffer, outputArray)

            val outputTokens = outputArray[0].takeWhile { it != tok.eosTokenId }

            val outputText = tok.decode(outputTokens)

            parseResponse(outputText, tools)
        } catch (e: Exception) {
            LlmResponse.Error("Inference failed: ${e.message}")
        }
    }

    private fun buildPrompt(messages: List<ChatMessage>, tools: List<Tool>): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>user\n")

        for (msg in messages) {
            when (msg.role) {
                "system" -> {
                    sb.append(msg.content ?: "")
                    sb.append("\n")
                }
                "user" -> {
                    sb.append(msg.content ?: "")
                    sb.append("\n")
                }
                "assistant" -> {
                    sb.append("<end_of_turn>\n<start_of_turn>model\n")
                    sb.append(msg.content ?: "")
                    sb.append("\n")
                }
                "tool" -> {
                    sb.append("<end_of_turn>\n<start_of_turn>user\n")
                    sb.append("Tool result: ${msg.content}\n")
                }
            }
        }

        if (tools.isNotEmpty()) {
            sb.append("\nAvailable tools:\n")
            tools.forEach { tool ->
                sb.append("- ${tool.name}: ${tool.description}\n")
                sb.append("  Parameters: ${tool.parameters}\n")
            }
            sb.append("\nTo call a tool, respond with: [TOOL_CALL] tool_name({\"param\": \"value\"})\n")
        }

        sb.append("<end_of_turn>\n<start_of_turn>model\n")

        return sb.toString()
    }

    private fun parseResponse(text: String, tools: List<Tool>): LlmResponse {
        val toolCallPattern = Regex("""\[TOOL_CALL\]\s*(\w+)\s*\((.+?)\)""")
        val toolMatch = toolCallPattern.find(text)

        if (toolMatch != null) {
            val toolName = toolMatch.groupValues[1]
            val argsStr = toolMatch.groupValues[2]

            if (tools.any { it.name == toolName }) {
                return LlmResponse.ToolCall(
                    name = toolName,
                    arguments = argsStr
                )
            }
        }

        val verdictPattern = Regex("""\{[^{}]*"verdict"\s*:\s*"(SAFE|SCAM|SUSPICIOUS)"[^{}]*\}""")
        val verdictMatch = verdictPattern.find(text)
        if (verdictMatch != null) {
            return LlmResponse.Text(text)
        }

        return LlmResponse.Text(text)
    }

    private fun loadModelFile(file: File): MappedByteBuffer {
        val inputStream = FileInputStream(file)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        tokenizer = null
    }
}

class GemmaTokenizer(private val context: Context) {

    val padTokenId = 0
    val eosTokenId = 1
    val bosTokenId = 2

    private var vocab: Map<String, Int> = emptyMap()
    private var idToToken: Map<Int, String> = emptyMap()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val assets = context.assets
            val vocabFile = File(context.filesDir, "tokenizer/vocab.json")

            if (!vocabFile.exists()) {
                vocabFile.parentFile?.mkdirs()
                assets.open("tokenizer/vocab.json").use { input ->
                    vocabFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val jsonStr = vocabFile.readText()
            val jsonObj = org.json.JSONObject(jsonStr)
            val mutableVocab = mutableMapOf<String, Int>()
            jsonObj.keys().forEach { key ->
                mutableVocab[key] = jsonObj.getInt(key)
            }
            vocab = mutableVocab
            idToToken = vocab.entries.associate { (k, v) -> v to k }

        } catch (e: Exception) {
            createMinimalVocab()
        }
    }

    private fun createMinimalVocab() {
        val specialTokens = mapOf(
            "<pad>" to padTokenId,
            "<eos>" to eosTokenId,
            "<bos>" to bosTokenId,
            "<start_of_turn>" to 3,
            "<end_of_turn>" to 4,
            "<turn>" to 5
        )
        vocab = specialTokens.toMutableMap()
        idToToken = vocab.entries.associate { (k, v) -> v to k }
    }

    fun encode(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        tokens.add(bosTokenId)

        val words = text.split(Regex("""(\s+)"""))
        for (word in words) {
            if (word.isBlank()) continue
            val wordLower = word.lowercase()
            val id = vocab[wordLower] ?: vocab[word] ?: encodeSubword(word)
            if (id != null) {
                tokens.add(id)
            }
        }

        return tokens.take(maxInputLength())
    }

    private fun encodeSubword(word: String): Int? {
        for (i in 1..word.length) {
            val prefix = word.substring(0, i)
            val id = vocab[prefix] ?: vocab[prefix.lowercase()]
            if (id != null) return id
        }
        return vocab["<unk>"] ?: 3
    }

    fun decode(tokens: List<Int>): String {
        val sb = StringBuilder()
        for (token in tokens) {
            if (token == eosTokenId || token == padTokenId) break
            val piece = idToToken[token] ?: ""
            if (piece.startsWith("<") && piece.endsWith(">")) continue
            sb.append(piece)
        }
        return sb.toString().trim()
    }

    private fun maxInputLength() = 512
}
