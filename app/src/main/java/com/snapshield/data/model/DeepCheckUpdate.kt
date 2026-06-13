package com.snapshield.data.model

sealed class DeepCheckUpdate {
    data class Step(val message: String, val progress: Int) : DeepCheckUpdate()
    data class FoundEvidence(val item: EvidenceItem) : DeepCheckUpdate()
    data class FinalVerdict(val verdict: DeepCheckVerdict) : DeepCheckUpdate()
    data class Error(val reason: String) : DeepCheckUpdate()
}
