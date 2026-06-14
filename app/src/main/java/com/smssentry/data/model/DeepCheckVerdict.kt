package com.smssentry.data.model

data class DeepCheckVerdict(
    val isScam: Boolean,
    val summary: String,
    val threatType: String?,
    val evidence: List<EvidenceItem>,
    val recommendedActions: List<String>,
    val educationalExplanation: String = "",
    val verdictLabel: String = if (isScam) "SCAM" else "SAFE"
)
