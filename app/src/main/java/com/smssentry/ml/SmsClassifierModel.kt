package com.smssentry.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.smssentry.data.model.ClassificationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicBoolean

class SmsClassifierModel(private val context: Context) {

    private val initDeferred = CompletableDeferred<Boolean>()
    private val isInitializing = AtomicBoolean(false)
    private var ortSession: OrtSession? = null
    private var tokenizer: BertTokenizer? = null
    private val ortEnv = OrtEnvironment.getEnvironment()

    val isReady: Boolean get() = initDeferred.isCompleted && ortSession != null

    fun preload() {
        if (initDeferred.isCompleted || isInitializing.compareAndSet(false, true)) {
            if (initDeferred.isCompleted) return
            Thread {
                try {
                    doInitialize()
                    initDeferred.complete(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Preload failed", e)
                    initDeferred.complete(false)
                } finally {
                    isInitializing.set(false)
                }
            }.start()
        }
    }

    suspend fun awaitReady() {
        initDeferred.await()
    }

    private fun doInitialize() {
        val modelBytes = context.assets.open("sms_classifier_quantized.onnx").readBytes()
        ortSession = ortEnv.createSession(modelBytes)
        val vocabText = context.assets.open("vocab.txt").bufferedReader().readText()
        tokenizer = BertTokenizer(vocabText)
        Log.d(TAG, "BERT model loaded successfully")
    }

    suspend fun classify(smsText: String): ClassificationResult = withContext(Dispatchers.IO) {
        awaitReady()
        val session = ortSession ?: return@withContext ruleBasedClassify(smsText)
        val tok = tokenizer ?: return@withContext ruleBasedClassify(smsText)

        try {
            val (inputIds, attentionMask) = tok.tokenize(smsText)

            val inputIdsTensor = OnnxTensor.createTensor(
                ortEnv, LongBuffer.wrap(inputIds), longArrayOf(1, 128)
            )
            val maskTensor = OnnxTensor.createTensor(
                ortEnv, LongBuffer.wrap(attentionMask), longArrayOf(1, 128)
            )

            val output = session.run(
                mapOf("input_ids" to inputIdsTensor, "attention_mask" to maskTensor)
            )

            val logits = (output[0].value as Array<FloatArray>)[0]
            val spamProb = softmax(logits)[1].toFloat()

            val ruleResult = ruleBasedClassify(smsText)
            val combinedScore = (spamProb * 0.6f) + (ruleResult.riskScore / 100f * 0.4f)

            when {
                combinedScore > 0.75f -> ClassificationResult(
                    label = "SCAM",
                    confidence = combinedScore,
                    riskScore = (combinedScore * 100).toInt(),
                    reasoning = "AI detected spam patterns (${(spamProb * 100).toInt()}% spam probability)",
                    isScam = true
                )
                combinedScore > 0.45f -> ClassificationResult(
                    label = "SUSPICIOUS",
                    confidence = combinedScore,
                    riskScore = (combinedScore * 100).toInt(),
                    reasoning = "Some suspicious patterns detected (${(spamProb * 100).toInt()}% spam probability)",
                    isScam = false
                )
                else -> ClassificationResult(
                    label = "SAFE",
                    confidence = 1f - combinedScore,
                    riskScore = (combinedScore * 100).toInt(),
                    reasoning = "Likely legitimate message (${(spamProb * 100).toInt()}% spam probability)",
                    isScam = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "BERT inference failed, falling back to rules", e)
            ruleBasedClassify(smsText)
        }
    }

    private fun softmax(logits: FloatArray): DoubleArray {
        val max = logits.max()
        val exp = logits.map { Math.exp((it - max).toDouble()) }
        val sum = exp.sum()
        return exp.map { it / sum }.toDoubleArray()
    }

    fun ruleBasedClassify(text: String): ClassificationResult {
        val lower = text.lowercase()

        val safePatterns = listOf(
            Regex("\\botp\\b"), Regex("\\bone.?time.?password\\b"),
            Regex("\\btransaction\\b"), Regex("\\bstatement\\b"),
            Regex("do not share"), Regex("never share"),
            Regex("\\bdelivery\\b"), Regex("\\border\\b"),
            Regex("\\bappointment\\b"), Regex("\\breminder\\b")
        )

        val scamPatterns = listOf(
            Regex("click here") to 25,
            Regex("verify.{0,20}account") to 20,
            Regex("account.{0,20}suspend") to 25,
            Regex("won.{0,30}prize|lottery|reward") to 30,
            Regex("claim.{0,20}now") to 25,
            Regex("free.{0,20}gift") to 20,
            Regex("urgent.{0,20}action") to 20,
            Regex("[a-z0-9-]+\\.(xyz|buzz|win|tk|ml|ga|cf)") to 35,
            Regex("your.{0,20}(bank|card|account).{0,20}(suspend|block|verify)") to 30,
            Regex("\\$[0-9,]+.{0,20}(won|prize|reward|claim)") to 30
        )

        val safeScore = safePatterns.count { it.containsMatchIn(lower) }
        var riskScore = 0
        val triggeredReasons = mutableListOf<String>()

        for ((pattern, score) in scamPatterns) {
            if (pattern.containsMatchIn(lower)) {
                riskScore += score
                triggeredReasons.add(pattern.pattern)
            }
        }

        riskScore = (riskScore - (safeScore * 15)).coerceAtLeast(0).coerceAtMost(100)

        return when {
            riskScore >= 50 -> ClassificationResult(
                label = "SCAM", confidence = 0.85f, riskScore = riskScore,
                reasoning = "Matched scam patterns: ${triggeredReasons.take(2).joinToString(", ")}",
                isScam = true
            )
            riskScore >= 25 -> ClassificationResult(
                label = "SUSPICIOUS", confidence = 0.65f, riskScore = riskScore,
                reasoning = "Some suspicious patterns found", isScam = false
            )
            else -> ClassificationResult(
                label = "SAFE", confidence = 0.9f, riskScore = riskScore,
                reasoning = "No significant scam indicators", isScam = false
            )
        }
    }

    fun close() { ortSession?.close() }

    companion object {
        private const val TAG = "SmsClassifierModel"
    }
}
