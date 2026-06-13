package com.smssentry.deepcheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AllowlistEntry::class, HistoryEntry::class], version = 1, exportSchema = false)
abstract class DeepCheckDatabase : RoomDatabase() {
    abstract fun allowlistDao(): AllowlistDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: DeepCheckDatabase? = null

        fun getInstance(context: Context): DeepCheckDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeepCheckDatabase::class.java,
                    "deepcheck.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
