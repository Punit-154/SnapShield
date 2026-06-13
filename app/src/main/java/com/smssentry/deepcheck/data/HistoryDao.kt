package com.smssentry.deepcheck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE hash = :hash LIMIT 1")
    suspend fun get(hash: String): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE timestamp < :cutoffEpochMillis")
    suspend fun pruneOlderThan(cutoffEpochMillis: Long)
}
