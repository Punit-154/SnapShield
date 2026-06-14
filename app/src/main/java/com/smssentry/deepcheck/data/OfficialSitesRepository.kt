package com.smssentry.deepcheck.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

open class OfficialSitesRepository {

    protected val sites: Map<String, String>

    constructor(context: Context) {
        val json = Json { ignoreUnknownKeys = true }
        val inputStream = context.assets.open("official_sites.json")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val raw = reader.readText()
        reader.close()

        sites = try {
            json.decodeFromString<Map<String, String>>(raw).mapKeys { it.key.lowercase() }
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

    internal constructor(sitesMap: Map<String, String>) {
        sites = sitesMap.mapKeys { it.key.lowercase() }
    }

    open fun lookupOfficialDomain(brandName: String): String? {
        return sites[brandName.lowercase()]
    }

    open fun findMatchingBrand(text: String): String? {
        val lowerText = text.lowercase()
        return sites.keys.find { brand -> lowerText.contains(brand) }
    }
}
