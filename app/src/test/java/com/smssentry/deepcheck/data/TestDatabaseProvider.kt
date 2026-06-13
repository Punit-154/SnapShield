package com.smssentry.deepcheck.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

abstract class TestDatabaseProvider {

    protected lateinit var database: DeepCheckDatabase
    protected lateinit var allowlistDao: AllowlistDao
    protected lateinit var historyDao: HistoryDao

    @Before
    open fun setupDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeepCheckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        allowlistDao = database.allowlistDao()
        historyDao = database.historyDao()
    }

    @After
    open fun closeDatabase() {
        database.close()
    }
}
