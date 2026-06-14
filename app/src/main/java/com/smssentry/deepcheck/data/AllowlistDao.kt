package com.smssentry.deepcheck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AllowlistDao {
    @Query("SELECT EXISTS(SELECT 1 FROM allowlist WHERE id = :sender AND type = 'sender')")
    suspend fun containsSender(sender: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM allowlist WHERE id = :domain AND type = 'domain')")
    suspend fun containsDomain(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AllowlistEntry)

    @Query("DELETE FROM allowlist WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM allowlist")
    suspend fun all(): List<AllowlistEntry>
}
