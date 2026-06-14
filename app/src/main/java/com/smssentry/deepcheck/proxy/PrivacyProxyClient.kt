package com.smssentry.deepcheck.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class WhoisResult(
    val creationDate: LocalDate?,
    val registrar: String?
)

class PrivacyProxyClient(private val baseUrl: String?) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var lastHealthCheckTime: Long = 0L

    @Volatile
    private var lastHealthCheckResult: Boolean = false

    suspend fun isAvailable(): Boolean {
        if (baseUrl.isNullOrBlank()) return false

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
                val success = response.isSuccessful
                lastHealthCheckResult = success
                lastHealthCheckTime = now
                success
            }
        } catch (e: Exception) {
            lastHealthCheckResult = false
            lastHealthCheckTime = now
            false
        }
    }

    suspend fun whois(domain: String): WhoisResult {
        if (baseUrl.isNullOrBlank()) throw IllegalStateException("Proxy not configured")

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/whois?domain=$domain")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val result = json.decodeFromString<WhoisResponse>(body)
            WhoisResult(
                creationDate = result.creationDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } },
                registrar = result.registrar
            )
        }
    }

    suspend fun fetchPage(url: String): String {
        if (baseUrl.isNullOrBlank()) throw IllegalStateException("Proxy not configured")

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/fetch-page?url=$url")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.body?.string() ?: ""
        }
    }
}

@Serializable
private data class WhoisResponse(
    val creationDate: String? = null,
    val registrar: String? = null
)
