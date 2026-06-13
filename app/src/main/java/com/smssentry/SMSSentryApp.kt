package com.smssentry

import android.app.Application
import com.smssentry.deepcheck.data.DeepCheckDatabase
import com.smssentry.deepcheck.data.HistoryDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

@HiltAndroidApp
class SMSSentryApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        pruneHistory()
    }

    private fun pruneHistory() {
        appScope.launch {
            try {
                val db = DeepCheckDatabase.getInstance(this@SMSSentryApp)
                val cutoff = Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli()
                db.historyDao().pruneOlderThan(cutoff)
            } catch (e: Exception) {
                // Database may not be initialized yet
            }
        }
    }
}
