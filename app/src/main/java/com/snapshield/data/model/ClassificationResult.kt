package com.snapshield.data.model

data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val riskScore: Int,
    val reasoning: String,
    val isScam: Boolean
)
