package com.smssentry.deepcheck.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

open class OfficialSitesRepository {

    protected val sites: Map<String, String> by lazy { loadSites() }
    private var contextRef: Context? = null

    constructor(context: Context) {
        contextRef = context
    }

    internal constructor(sitesMap: Map<String, String>) {
        // For testing — bypasses lazy loading
        contextRef = null
    }

    private fun loadSites(): Map<String, String> {
        val ctx = contextRef ?: return mapOf(
            "google" to "google.com",
            "paypal" to "paypal.com",
            "netflix" to "netflix.com",
            "amazon" to "amazon.com",
            "apple" to "apple.com",
            "microsoft" to "microsoft.com",
            "facebook" to "facebook.com",
            "instagram" to "instagram.com"
        ).mapKeys { it.key.lowercase() }
        val json = Json { ignoreUnknownKeys = true }
        return try {
            ctx.assets.open("official_sites.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val raw = reader.readText()
                    json.decodeFromString<Map<String, String>>(raw).mapKeys { it.key.lowercase() }
                }
            }
        } catch (e: Exception) {
            mapOf(
                "google" to "google.com",
                "paypal" to "paypal.com",
                "netflix" to "netflix.com",
                "amazon" to "amazon.com",
                "apple" to "apple.com",
                "microsoft" to "microsoft.com",
                "facebook" to "facebook.com",
                "instagram" to "instagram.com"
            ).mapKeys { it.key.lowercase() }
        }
    }

    open fun lookupOfficialDomain(brandName: String): String? {
        return sites[brandName.lowercase()]
    }

    open fun findMatchingBrand(text: String): String? {
        val lowerText = text.lowercase()
        return sites.keys.find { brand -> lowerText.contains(brand) }
    }
}
