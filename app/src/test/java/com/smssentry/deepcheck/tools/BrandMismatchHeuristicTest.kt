package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.data.OfficialSitesRepository
import org.junit.Assert.*
import org.junit.Test

class BrandMismatchHeuristicTest {

    private val officialSites = OfficialSitesRepository(
        mapOf("hsbc" to "hsbc.co.in", "sbi" to "onlinesbi.sbi")
    )

    @Test
    fun `brand mentioned with matching domain returns null`() {
        val result = BrandMismatchHeuristic.check(
            "HSBC notification",
            listOf("https://hsbc.co.in/login"),
            officialSites
        )
        assertNull(result)
    }

    @Test
    fun `brand mentioned with mismatched domain returns non-null`() {
        val result = BrandMismatchHeuristic.check(
            "HSBC notification",
            listOf("https://hsbc-secure-login.xyz/verify"),
            officialSites
        )
        assertNotNull(result)
        assertTrue(result!!.contains("HSBC", ignoreCase = true))
    }

    @Test
    fun `subdomain spoofing triggers mismatch`() {
        val result = BrandMismatchHeuristic.check(
            "HSBC account alert",
            listOf("https://hsbc.co.in.verify-account.xyz/login"),
            officialSites
        )
        assertNotNull(result)
        assertTrue(result!!.contains("HSBC", ignoreCase = true))
    }

    @Test
    fun `no brand mentioned returns null`() {
        val result = BrandMismatchHeuristic.check(
            "Hey, meeting tomorrow?",
            listOf("https://example.com"),
            officialSites
        )
        assertNull(result)
    }

    @Test
    fun `brand mentioned with no URL returns null`() {
        val result = BrandMismatchHeuristic.check(
            "HSBC notification: call us",
            emptyList(),
            officialSites
        )
        assertNull(result)
    }
}
