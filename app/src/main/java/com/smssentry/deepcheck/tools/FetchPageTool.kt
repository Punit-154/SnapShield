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

    /**
     * Blocked IP ranges to prevent SSRF attacks:
     * - 127.0.0.0/8     (loopback)
     * - 10.0.0.0/8      (RFC1918 private)
     * - 172.16.0.0/12   (RFC1918 private)
     * - 192.168.0.0/16  (RFC1918 private)
     * - 169.254.0.0/16  (link-local / cloud metadata)
     * - 0.0.0.0/8       (unspecified)
     * - ::1, fe80::/10  (IPv6 loopback/link-local)
     */
    private fun isBlockedAddress(address: InetAddress): Boolean {
        return address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress ||
            // Explicit check for 169.254.x.x (cloud metadata endpoint)
            address.hostAddress?.startsWith("169.254.") == true
    }

    private fun isHostSafe(hostname: String): Boolean {
        return try {
            val addresses = InetAddress.getAllByName(hostname)
            addresses.none { isBlockedAddress(it) }
        } catch (e: Exception) {
            false // DNS resolution failed — block
        }
    }

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return@withContext "Invalid URL scheme."

        // SSRF protection: validate hostname resolves to a public IP
        val hostname = try {
            URI(url).host ?: return@withContext "Invalid URL: no hostname."
        } catch (e: Exception) {
            return@withContext "Invalid URL format."
        }

        if (!isHostSafe(hostname)) {
            return@withContext "Blocked: URL resolves to a private/internal address."
        }

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
