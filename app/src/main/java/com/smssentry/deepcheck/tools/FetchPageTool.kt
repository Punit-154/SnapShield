package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.proxy.PrivacyProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
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

        if (!isSafeUrl(url))
            return@withContext "Blocked: URL targets a private/internal address."

        return@withContext try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.take(500) ?: ""
                "Page content (first 500 chars): $body"
            }
        } catch (e: Exception) {
            "Fetch failed: ${e.message}"
        }
    }

    private fun isSafeUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return false
            val addr = InetAddress.getByName(host)
            val ip = addr.hostAddress ?: return false
            !addr.isLoopbackAddress &&
                !addr.isAnyLocalAddress &&
                !addr.isLinkLocalAddress &&
                !ip.startsWith("10.") &&
                !ip.startsWith("172.16.") && !ip.startsWith("172.17.") &&
                !ip.startsWith("172.18.") && !ip.startsWith("172.19.") &&
                !ip.startsWith("172.2") && !ip.startsWith("172.3") &&
                !ip.startsWith("192.168.") &&
                !ip.startsWith("169.254.")
        } catch (e: Exception) {
            false
        }
    }
}
