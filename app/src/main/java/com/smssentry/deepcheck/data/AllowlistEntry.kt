package com.smssentry.deepcheck.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "allowlist", indices = [Index(value = ["id", "type"], unique = true)])
data class AllowlistEntry(
    @PrimaryKey val id: String,
    val type: String,
    val addedByUser: Boolean
)
