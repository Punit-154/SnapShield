package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FetchPageTool(private val proxyClient: PrivacyProxyClient?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return@withContext "Invalid URL scheme."
        return@withContext try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()?.take(500) ?: ""
            response.close()
            "Page content (first 500 chars): $body"
        } catch (e: Exception) {
            "Fetch failed: ${e.message}"
        }
    }
}
