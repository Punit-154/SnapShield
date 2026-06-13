package com.snapshield.data.model

data class DeepCheckVerdict(
    val isScam: Boolean,
    val summary: String,
    val threatType: String?,
    val evidence: List<EvidenceItem>,
    val recommendedActions: List<String>
)
