package com.smssentry.deepcheck.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class ReputationDb(private val context: Context) {

    private var db: SQLiteDatabase? = null
    private var isAvailable = false

    init {
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
        }
    }

    fun isScam(domain: String): Boolean {
        return threatType(domain) != null
    }

    fun threatType(domain: String): String? {
        val database = db ?: return null
        if (!isAvailable) return null
        return try {
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

    fun close() {
        db?.close()
        db = null
        isAvailable = false
    }
}
