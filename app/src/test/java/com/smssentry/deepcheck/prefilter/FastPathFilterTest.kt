package com.smssentry.deepcheck.prefilter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smssentry.deepcheck.data.AllowlistEntry
import com.smssentry.deepcheck.data.TestDatabaseProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FastPathFilterTest : TestDatabaseProvider() {

    private lateinit var context: Context

    @Before
    override fun setupDatabase() {
        super.setupDatabase()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `allowlist sender match returns SAFE`() = runBlocking {
        allowlistDao.insert(AllowlistEntry("+1234567890", "sender", false))
        val result = FastPathFilter.filter(context, "Hello", "+1234567890", allowlistDao, historyDao)
        assertEquals("SAFE", result.verdict)
        assertEquals(0.99f, result.confidence)
    }

    @Test
    fun `suspicious TLD returns SCAM`() = runBlocking {
        val result = FastPathFilter.filter(
            context,
            "Click here: http://scam.xyz/verify",
            "Unknown",
            allowlistDao,
            historyDao
        )
        assertEquals("SCAM", result.verdict)
    }

    @Test
    fun `raw IP URL returns SCAM`() = runBlocking {
        val result = FastPathFilter.filter(
            context,
            "Visit http://192.168.1.1/phish now",
            "Unknown",
            allowlistDao,
            historyDao
        )
        assertEquals("SCAM", result.verdict)
    }

    @Test
    fun `OTP without links returns SAFE`() = runBlocking {
        val result = FastPathFilter.filter(
            context,
            "Your OTP is 4829. Do not share.",
            "Bank",
            allowlistDao,
            historyDao
        )
        assertEquals("SAFE", result.verdict)
    }

    @Test
    fun `clean message falls through`() = runBlocking {
        val result = FastPathFilter.filter(
            context,
            "Hi, are we still meeting for lunch?",
            "+44 7700 900123",
            allowlistDao,
            historyDao
        )
        assertNull(result.verdict)
    }

    @Test
    fun `extractUrls handles http and bare domains`() {
        val urls = FastPathFilter.extractUrls(
            "Visit https://example.com or bit.ly/abc123 or secure-bank.com/login"
        )
        assertEquals(3, urls.size)
        assertTrue(urls[0].startsWith("https://"))
        assertTrue(urls[1].startsWith("https://"))
        assertTrue(urls[2].startsWith("https://"))
    }

    @Test
    fun `extractDomains handles multi-part TLDs`() {
        val domains = FastPathFilter.extractDomains(
            listOf("https://hsbc.co.in/login", "https://example.com")
        )
        assertEquals(2, domains.size)
        assertTrue(domains.contains("hsbc.co.in"))
        assertTrue(domains.contains("example.com"))
    }

    @Test
    fun `first matching rule wins`() = runBlocking {
        allowlistDao.insert(AllowlistEntry("+1234567890", "sender", false))
        val result = FastPathFilter.filter(
            context,
            "Click here: http://scam.xyz/verify",
            "+1234567890",
            allowlistDao,
            historyDao
        )
        assertEquals("SAFE", result.verdict)
        assertEquals(0.99f, result.confidence)
    }
}
