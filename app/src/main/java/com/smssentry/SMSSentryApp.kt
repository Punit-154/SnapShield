package com.smssentry

import android.app.Application
import android.util.Log
import com.smssentry.deepcheck.data.DeepCheckDatabase
import com.smssentry.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltAndroidApp
class SMSSentryApp : Application() {

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

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
                Log.e("SMSSentryApp", "Failed to prune history", e)
            }
        }
    }
}
