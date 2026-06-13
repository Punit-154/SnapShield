package com.smssentry.deepcheck.session

import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.HistoryDao

data class OfflineEvaluationRubric(
    val id: String,
    val name: String,
    val smsText: String,
    val sender: String,
    val expectedVerdict: String,
    val expectedConfidenceRange: ClosedFloatingPointRange<Float>,
    val keyEvidence: String,
    val maxTurns: Int,
    val setup: (suspend (AllowlistDao, HistoryDao) -> Unit)? = null
)
