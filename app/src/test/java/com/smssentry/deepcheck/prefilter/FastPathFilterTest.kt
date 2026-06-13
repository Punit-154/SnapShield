package com.smssentry.deepcheck.prefilter

import com.smssentry.deepcheck.data.AllowlistDao
import com.smssentry.deepcheck.data.AllowlistEntry
import com.smssentry.deepcheck.data.HistoryDao
import com.smssentry.deepcheck.data.HistoryEntry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FastPathFilterTest {

    private lateinit var allowlistDao: FakeAllowlistDao
    private lateinit var historyDao: FakeHistoryDao

    @Before
    fun setup() {
        allowlistDao = FakeAllowlistDao()
        historyDao = FakeHistoryDao()
    }

    @Test
    fun `allowlist sender match returns SAFE`() = kotlinx.coroutines.runBlocking {
        allowlistDao.addSender("+1234567890")
        val result = FastPathFilter.filter("Hello", "+1234567890", allowlistDao, historyDao)
        assertEquals("SAFE", result.verdict)
        assertEquals(0.99f, result.confidence)
    }

    @Test
    fun `suspicious TLD returns SCAM`() = kotlinx.coroutines.runBlocking {
        val result = FastPathFilter.filter(
            "Click here: http://scam.xyz/verify",
            "Unknown",
            allowlistDao,
            historyDao
        )
        assertEquals("SCAM", result.verdict)
    }

    @Test
    fun `raw IP URL returns SCAM`() = kotlinx.coroutines.runBlocking {
        val result = FastPathFilter.filter(
            "Visit http://192.168.1.1/phish now",
            "Unknown",
            allowlistDao,
            historyDao
        )
        assertEquals("SCAM", result.verdict)
    }

    @Test
    fun `OTP without links returns SAFE`() = kotlinx.coroutines.runBlocking {
        val result = FastPathFilter.filter(
            "Your OTP is 4829. Do not share.",
            "Bank",
            allowlistDao,
            historyDao
        )
        assertEquals("SAFE", result.verdict)
    }

    @Test
    fun `clean message falls through`() = kotlinx.coroutines.runBlocking {
        val result = FastPathFilter.filter(
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
    fun `first matching rule wins`() = kotlinx.coroutines.runBlocking {
        allowlistDao.addSender("+1234567890")
        val result = FastPathFilter.filter(
            "Click here: http://scam.xyz/verify",
            "+1234567890",
            allowlistDao,
            historyDao
        )
        assertEquals("SAFE", result.verdict)
        assertEquals(0.99f, result.confidence)
    }
}

class FakeAllowlistDao : AllowlistDao {
    private val senders = mutableSetOf<String>()
    private val domains = mutableSetOf<String>()

    fun addSender(sender: String) { senders.add(sender) }
    fun addDomain(domain: String) { domains.add(domain) }

    override suspend fun containsSender(sender: String) = sender in senders
    override suspend fun containsDomain(domain: String) = domain in domains
    override suspend fun insert(entry: AllowlistEntry) {
        if (entry.type == "sender") senders.add(entry.id) else domains.add(entry.id)
    }
    override suspend fun delete(id: String) { senders.remove(id); domains.remove(id) }
    override suspend fun all() = emptyList<AllowlistEntry>()
}

class FakeHistoryDao : HistoryDao {
    private val entries = mutableMapOf<String, HistoryEntry>()

    fun addEntry(hash: String, verdict: String) {
        entries[hash] = HistoryEntry(hash, verdict, 0.9f, System.currentTimeMillis(), 1)
    }

    override suspend fun get(hash: String) = entries[hash]
    override suspend fun insert(entry: HistoryEntry) { entries[entry.hash] = entry }
    override suspend fun pruneOlderThan(cutoffEpochMillis: Long) {
        entries.values.removeAll { it.timestamp < cutoffEpochMillis }
    }
}
