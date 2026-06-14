package com.smssentry.deepcheck.proxy

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class WhoisResult(
    val creationDate: LocalDate?,
    val registrar: String?
)

class PrivacyProxyClient(private val baseUrl: String?) {

    private val TAG = "PrivacyProxyClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // ── Health check cache ────────────────────────────────────────────
    @Volatile
    private var lastHealthCheckTime: Long = 0L

    @Volatile
    private var lastHealthCheckResult: Boolean = false

    // ── Circuit breaker ───────────────────────────────────────────────
    // After CIRCUIT_BREAKER_THRESHOLD consecutive failures the proxy is
    // considered "open" and all calls are short-circuited for
    // CIRCUIT_BREAKER_COOLDOWN_MS to avoid hammering a broken service.
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenSince = AtomicLong(0L)

    companion object {
        private const val CIRCUIT_BREAKER_THRESHOLD = 3
        private const val CIRCUIT_BREAKER_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Returns `true` if the circuit breaker is open (proxy is presumed down).
     * After the cooldown period the circuit transitions to half-open and the
     * next real call decides whether to re-open or close it.
     */
    private fun isCircuitOpen(): Boolean {
        if (consecutiveFailures.get() < CIRCUIT_BREAKER_THRESHOLD) return false
        val openedAt = circuitOpenSince.get()
        if (openedAt == 0L) return false
        val elapsed = System.currentTimeMillis() - openedAt
        if (elapsed >= CIRCUIT_BREAKER_COOLDOWN_MS) {
            // Cooldown expired → half-open: reset and let the next call try.
            consecutiveFailures.set(0)
            circuitOpenSince.set(0L)
            Log.d(TAG, "Circuit breaker half-open, allowing next attempt")
            return false
        }
        return true
    }

    private fun recordSuccess() {
        consecutiveFailures.set(0)
        circuitOpenSince.set(0L)
    }

    private fun recordFailure() {
        val count = consecutiveFailures.incrementAndGet()
        if (count >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenSince.compareAndSet(0L, System.currentTimeMillis())
            Log.w(TAG, "Circuit breaker OPEN after $count consecutive failures")
        }
    }

    suspend fun isAvailable(): Boolean {
        if (baseUrl.isNullOrBlank()) return false
        if (isCircuitOpen()) {
            Log.d(TAG, "isAvailable: circuit open, returning false")
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastHealthCheckTime < 5 * 60 * 1000) {
            return lastHealthCheckResult
        }

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/health")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.use {
                    val success = it.isSuccessful
                    lastHealthCheckResult = success
                    lastHealthCheckTime = now
                    if (success) recordSuccess() else recordFailure()
                    success
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Health check timed out: ${e.message}")
            recordFailure()
            lastHealthCheckResult = false
            lastHealthCheckTime = now
            false
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Health check DNS failure: ${e.message}")
            recordFailure()
            lastHealthCheckResult = false
            lastHealthCheckTime = now
            false
        } catch (e: IOException) {
            Log.w(TAG, "Health check I/O error: ${e.message}")
            recordFailure()
            lastHealthCheckResult = false
            lastHealthCheckTime = now
            false
        } catch (e: Exception) {
            Log.e(TAG, "Health check unexpected error", e)
            recordFailure()
            lastHealthCheckResult = false
            lastHealthCheckTime = now
            false
        }
    }

    suspend fun whois(domain: String): WhoisResult {
        if (baseUrl.isNullOrBlank()) throw IllegalStateException("Proxy not configured")
        if (isCircuitOpen()) throw IOException("Proxy circuit breaker is open — skipping request")

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/whois?domain=${java.net.URLEncoder.encode(domain, "UTF-8")}")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("WHOIS request failed: HTTP ${resp.code}")
                    }
                    val body = resp.body?.string() ?: throw IOException("Empty response body")
                    val result = json.decodeFromString<WhoisResponse>(body)
                    recordSuccess()
                    WhoisResult(
                        creationDate = result.creationDate?.let {
                            try { LocalDate.parse(it) } catch (e: Exception) { null }
                        },
                        registrar = result.registrar
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "WHOIS timed out for $domain: ${e.message}")
            recordFailure()
            throw IOException("WHOIS request timed out for $domain", e)
        } catch (e: UnknownHostException) {
            Log.w(TAG, "WHOIS DNS failure for $domain: ${e.message}")
            recordFailure()
            throw IOException("Cannot resolve proxy host for WHOIS", e)
        } catch (e: IOException) {
            recordFailure()
            throw e
        }
    }

    suspend fun fetchPage(url: String): String {
        if (baseUrl.isNullOrBlank()) throw IllegalStateException("Proxy not configured")
        if (isCircuitOpen()) throw IOException("Proxy circuit breaker is open — skipping request")

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/fetch-page?url=${java.net.URLEncoder.encode(url, "UTF-8")}")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "fetchPage failed: HTTP ${resp.code} for $url")
                        recordFailure()
                        return@withContext ""
                    }
                    recordSuccess()
                    resp.body?.string() ?: ""
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "fetchPage timed out for $url: ${e.message}")
            recordFailure()
            ""
        } catch (e: UnknownHostException) {
            Log.w(TAG, "fetchPage DNS failure for $url: ${e.message}")
            recordFailure()
            ""
        } catch (e: IOException) {
            Log.w(TAG, "fetchPage I/O error for $url: ${e.message}")
            recordFailure()
            ""
        } catch (e: Exception) {
            Log.e(TAG, "fetchPage unexpected error for $url", e)
            recordFailure()
            ""
        }
    }
}

@Serializable
private data class WhoisResponse(
    val creationDate: String? = null,
    val registrar: String? = null
)
