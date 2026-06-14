package com.smssentry.deepcheck.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index("timestamp")])
data class HistoryEntry(
    @PrimaryKey val hash: String,
    val verdict: String,
    val confidence: Float,
    val timestamp: Long,
    val evidenceCount: Int
)
