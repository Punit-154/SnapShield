package com.smssentry.deepcheck.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReputationDb(private val context: Context) {

    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var isAvailable = false
    @Volatile private var initialized = false

    /**
     * Lazily initializes the database on a background thread. Safe to call multiple
     * times — subsequent calls return immediately if already initialized.
     */
    private suspend fun ensureInitialized() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            if (initialized) return@withContext // double-checked locking
            try {
                val dbPath = context.getDatabasePath("phish_domains.db")
                if (!dbPath.exists()) {
                    dbPath.parentFile?.mkdirs()
                    context.assets.open("phish_domains.db").use { input ->
                        dbPath.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                db = SQLiteDatabase.openDatabase(
                    dbPath.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                isAvailable = true
            } catch (e: Exception) {
                isAvailable = false
                db = null
            } finally {
                initialized = true
            }
        }
    }

    suspend fun isScam(domain: String): Boolean = threatType(domain) != null

    suspend fun threatType(domain: String): String? {
        ensureInitialized()
        val database = db ?: return null
        if (!isAvailable) return null
        return withContext(Dispatchers.IO) {
            try {
                val cursor = database.rawQuery(
                    "SELECT type FROM phish_domains WHERE domain = ?",
                    arrayOf(domain.lowercase())
                )
                cursor.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun close() {
        db?.close()
        db = null
        isAvailable = false
        initialized = false
    }
}
