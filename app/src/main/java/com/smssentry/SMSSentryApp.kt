package com.smssentry

import android.app.Application
import android.util.Log
import com.smssentry.deepcheck.data.DeepCheckDatabase
import com.smssentry.sms.NotificationHelper
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
        NotificationHelper.createChannels(this)
        pruneHistory()
    }

    private fun pruneHistory() {
        appScope.launch {
            try {
                val db = DeepCheckDatabase.getInstance(this@SMSSentryApp)
                val cutoff = Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli()
                db.historyDao().pruneOlderThan(cutoff)
            } catch (e: Exception) {
                Log.e("SMSSentryApp", "Failed to prune history", e)
            }
        }
    }
}
