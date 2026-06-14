package com.smssentry.deepcheck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowlist")
data class AllowlistEntry(
    @PrimaryKey val id: String,
    val type: String,
    val addedByUser: Boolean
)
