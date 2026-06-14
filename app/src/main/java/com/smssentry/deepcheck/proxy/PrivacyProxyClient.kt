package com.smssentry.deepcheck.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

import java.net.URLEncoder

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
                client.newCall(request).execute().use { response ->
                    val success = response.isSuccessful
                    lastHealthCheckResult = success
                    lastHealthCheckTime = now
                    success
                }
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
            val encodedDomain = URLEncoder.encode(domain, "UTF-8")
            val request = Request.Builder()
                .url("$baseUrl/whois?domain=$encodedDomain")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")
                val result = json.decodeFromString<WhoisResponse>(body)
                WhoisResult(
                    creationDate = result.creationDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } },
                    registrar = result.registrar
                )
            }
        }
    }

    suspend fun fetchPage(url: String): String {
        if (baseUrl.isNullOrBlank()) throw IllegalStateException("Proxy not configured")

        return withContext(Dispatchers.IO) {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("$baseUrl/fetch-page?url=$encodedUrl")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }
    }
}

@Serializable
private data class WhoisResponse(
    val creationDate: String? = null,
    val registrar: String? = null
)
