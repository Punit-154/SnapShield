package com.smssentry.deepcheck.util

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized diagnostic logger for the Deep Check pipeline.
 *
 * All events are:
 * 1. Printed to Logcat with consistent tags
 * 2. Stored in a ring buffer accessible via [events] StateFlow
 * 3. Timed automatically when using [timed] blocks
 *
 * Filter in Logcat with: `tag:SMSentry`
 */
object Diagnostics {

    private const val TAG = "SMSentry"
    private const val MAX_EVENTS = 200

    data class Event(
        val id: Long,
        val timestamp: Long,
        val category: String,
        val message: String,
        val durationMs: Long? = null,
        val level: Level = Level.INFO
    ) {
        enum class Level { DEBUG, INFO, WARN, ERROR }

        override fun toString(): String {
            val dur = if (durationMs != null) " [${durationMs}ms]" else ""
            val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date(timestamp))
            return "[$time] [$category]$dur $message"
        }
    }

    private val counter = AtomicLong(0)
    private val buffer = ConcurrentLinkedDeque<Event>()
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    // ── Logging methods ──────────────────────────────────────────────

    fun d(category: String, message: String) = log(Event.Level.DEBUG, category, message)
    fun i(category: String, message: String) = log(Event.Level.INFO, category, message)
    fun w(category: String, message: String) = log(Event.Level.WARN, category, message)
    fun e(category: String, message: String, throwable: Throwable? = null) {
        log(Event.Level.ERROR, category, message)
        throwable?.let { Log.e(TAG, "[$category] $message", it) }
    }

    @PublishedApi
    internal fun log(level: Event.Level, category: String, message: String, durationMs: Long? = null) {
        val event = Event(
            id = counter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            category = category,
            message = message,
            durationMs = durationMs,
            level = level
        )
        buffer.addLast(event)
        while (buffer.size > MAX_EVENTS) buffer.pollFirst()
        _events.value = buffer.toList()

        val logMsg = "[$category] $message" + if (durationMs != null) " [${durationMs}ms]" else ""
        when (level) {
            Event.Level.DEBUG -> Log.d(TAG, logMsg)
            Event.Level.INFO  -> Log.i(TAG, logMsg)
            Event.Level.WARN  -> Log.w(TAG, logMsg)
            Event.Level.ERROR -> Log.e(TAG, logMsg)
        }
    }

    // ── Timed execution ──────────────────────────────────────────────

    /**
     * Execute [block] and log its duration.
     *
     * ```kotlin
     * val result = Diagnostics.timed("Engine", "initialize()") {
     *     engine.initialize()
     * }
     * ```
     */
    inline fun <T> timed(category: String, operation: String, block: () -> T): T {
        i(category, "▶ START $operation")
        val startNs = System.nanoTime()
        return try {
            val result = block()
            val ms = (System.nanoTime() - startNs) / 1_000_000
            log(Event.Level.INFO, category, "✓ DONE  $operation", ms)
            result
        } catch (ex: Exception) {
            val ms = (System.nanoTime() - startNs) / 1_000_000
            log(Event.Level.ERROR, category, "✗ FAIL  $operation — ${ex.message}", ms)
            throw ex
        }
    }

    /**
     * Suspend version of [timed].
     */
    suspend inline fun <T> timedSuspend(category: String, operation: String, crossinline block: suspend () -> T): T {
        i(category, "▶ START $operation")
        val startNs = System.nanoTime()
        return try {
            val result = block()
            val ms = (System.nanoTime() - startNs) / 1_000_000
            log(Event.Level.INFO, category, "✓ DONE  $operation", ms)
            result
        } catch (ex: Exception) {
            val ms = (System.nanoTime() - startNs) / 1_000_000
            log(Event.Level.ERROR, category, "✗ FAIL  $operation — ${ex.message}", ms)
            throw ex
        }
    }

    // ── Convenience constants ────────────────────────────────────────

    const val ENGINE  = "Engine"
    const val MODEL   = "Model"
    const val SESSION = "Session"
    const val TOOL    = "Tool"
    const val PARSE   = "Parse"
    const val UI      = "UI"
    const val REPO    = "Repository"
    const val DOWNLOAD = "Download"

    fun clear() {
        buffer.clear()
        _events.value = emptyList()
    }
}
