package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.data.AllowlistEntry
import com.smssentry.deepcheck.data.HistoryEntry
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.TestDatabaseProvider
import com.smssentry.deepcheck.model.LlmResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolExecutorTest : TestDatabaseProvider() {

    private val officialSites = OfficialSitesRepository(
        mapOf("hsbc" to "hsbc.co.in", "sbi" to "onlinesbi.sbi")
    )
    private var executor: ToolExecutor? = null

    @Before
    override fun setupDatabase() {
        super.setupDatabase()
        executor = ToolExecutor(allowlistDao, historyDao, null, officialSites, null)
    }

    @Test
    fun `lookup_allowlist sender match returns SAFE`() = runBlocking {
        allowlistDao.insert(AllowlistEntry("BankOfAmerica", "sender", false))
        val result = executor!!.execute(
            LlmResponse.ToolCall("lookup_allowlist", """{"sender":"BankOfAmerica"}""")
        )
        assertTrue(result.message.contains("SAFE"))
    }

    @Test
    fun `lookup_allowlist no match returns not in allowlist`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("lookup_allowlist", """{"sender":"UnknownSender"}""")
        )
        assertTrue(result.message.contains("Not in allowlist"))
    }

    @Test
    fun `lookup_allowlist domain match returns SAFE`() = runBlocking {
        allowlistDao.insert(AllowlistEntry("hsbc.co.in", "domain", false))
        val result = executor!!.execute(
            LlmResponse.ToolCall("lookup_allowlist", """{"domain":"hsbc.co.in"}""")
        )
        assertTrue(result.message.contains("SAFE"))
    }

    @Test
    fun `search_personal_db no match returns no match found`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("search_personal_db", """{"sender":"+1234","sms_prefix":"Hello worl"}""")
        )
        assertTrue(result.message.contains("No match found"))
    }

    @Test
    fun `search_personal_db with history entry returns evidence`() = runBlocking {
        val hash = com.smssentry.deepcheck.util.HashUtil.hashSms("+1234", "Hello worl")
        historyDao.insert(HistoryEntry(hash, "SCAM", 0.9f, System.currentTimeMillis(), 3))
        val result = executor!!.execute(
            LlmResponse.ToolCall("search_personal_db", """{"sender":"+1234","sms_prefix":"Hello worl"}""")
        )
        assertTrue(result is ToolResult.Evidence)
        assertTrue(result.message.contains("SCAM"))
    }

    @Test
    fun `offline_reputation_check with no URLs returns no URLs`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("offline_reputation_check", """{"urls":[]}""")
        )
        assertTrue(result.message.contains("No URLs provided"))
    }

    @Test
    fun `offline_reputation_check with clean URLs returns no bad`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("offline_reputation_check", """{"urls":["https://google.com"]}""")
        )
        assertTrue(result.message.contains("No known bad URLs"))
    }

    @Test
    fun `brand_mismatch_check with matching brand and domain returns no mismatch`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("brand_mismatch_check", """{"sms_text":"Your HSBC account","urls":["https://hsbc.co.in/login"]}""")
        )
        assertTrue(result.message.contains("No brand mismatch"))
    }

    @Test
    fun `brand_mismatch_check with mismatched domain returns evidence`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("brand_mismatch_check", """{"sms_text":"Your HSBC account","urls":["https://hsbc-secure.xyz/verify"]}""")
        )
        assertTrue(result is ToolResult.Evidence)
        assertTrue(result.message.contains("HSBC", ignoreCase = true))
    }

    @Test
    fun `brand_mismatch_check with no URLs returns no mismatch`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("brand_mismatch_check", """{"sms_text":"Your HSBC account","urls":[]}""")
        )
        assertTrue(result.message.contains("No brand mismatch"))
    }

    @Test
    fun `whois_lookup offline returns unavailable`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("whois_lookup", """{"domain":"evil.xyz"}""")
        )
        assertTrue(result.message.contains("unavailable"))
    }

    @Test
    fun `compare_official_site with matching domain returns matches`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("compare_official_site", """{"claimed_entity":"hsbc","linked_domain":"hsbc.co.in"}""")
        )
        assertTrue(result.message.contains("match"))
    }

    @Test
    fun `compare_official_site with mismatched domain returns evidence`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("compare_official_site", """{"claimed_entity":"hsbc","linked_domain":"hsbc-secure.xyz"}""")
        )
        assertTrue(result is ToolResult.Evidence)
        assertTrue(result.message.contains("does not match"))
    }

    @Test
    fun `compare_official_site with unknown entity returns unknown`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("compare_official_site", """{"claimed_entity":"FakeBrand","linked_domain":"fake.com"}""")
        )
        assertTrue(result.message.contains("Unknown entity"))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = executor!!.execute(
            LlmResponse.ToolCall("nonexistent_tool", """{}""")
        )
        assertTrue(result.message.contains("Unknown tool"))
    }
}
