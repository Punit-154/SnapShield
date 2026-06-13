package com.smssentry.data.model

data class InvestigationUiState(
    val progress: Int = 0,
    val currentStep: String? = null,
    val evidence: List<EvidenceItem> = emptyList(),
    val verdict: DeepCheckVerdict? = null,
    val error: String? = null
)
