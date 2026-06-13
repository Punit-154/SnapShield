package com.smssentry.deepcheck.session

import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.deepcheck.model.LlmResponse
import com.smssentry.deepcheck.prefilter.FakeAllowlistDao
import com.smssentry.deepcheck.prefilter.FakeHistoryDao
import com.smssentry.deepcheck.tools.FakeOfficialSitesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeepCheckSessionTest {

    private lateinit var allowlistDao: FakeAllowlistDao
    private lateinit var historyDao: FakeHistoryDao
    private val officialSites = FakeOfficialSitesRepository()

    @Before
    fun setup() {
        allowlistDao = FakeAllowlistDao()
        historyDao = FakeHistoryDao()
    }

    @Test
    fun `fast path short-circuit does not invoke LLM`() = runBlocking {
        allowlistDao.addSender("+1234567890")
        val engine = MockLlmEngine(listOf(
            LlmResponse.Error("Should not be called")
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val session = DeepCheckSession(
            engine, allowlistDao, historyDao, null, officialSites, null,
            "Hello", "+1234567890",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            }
        )
        session.run()
        assertTrue(updates.last() is DeepCheckUpdate.FinalVerdict)
        val verdict = (updates.last() as DeepCheckUpdate.FinalVerdict).verdict
        assertFalse(verdict.isScam)
    }

    @Test
    fun `model timeout triggers fallback verdict`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            LlmResponse.Error("timeout")
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val session = DeepCheckSession(
            engine, allowlistDao, historyDao, null, officialSites, null,
            "Suspicious message with http://evil.xyz link",
            "Scammer",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            }
        )
        session.run()
        assertTrue(updates.any { it is DeepCheckUpdate.FinalVerdict })
    }

    @Test
    fun `model error triggers fallback`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            LlmResponse.Error("Model crashed")
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val session = DeepCheckSession(
            engine, allowlistDao, historyDao, null, officialSites, null,
            "Click http://scam.xyz/verify now!",
            "Unknown",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            }
        )
        session.run()
        assertTrue(updates.any { it is DeepCheckUpdate.FinalVerdict })
    }

    @Test
    fun `happy path with tool call then verdict`() = runBlocking {
        val verdictJson = """{"verdict":"SAFE","confidence":0.95,"reasoning":"Domain is allowlisted.","evidence":["Domain verified"]}"""
        val engine = MockLlmEngine(listOf(
            LlmResponse.ToolCall("lookup_allowlist", """{"sender":"+1234567890"}"""),
            LlmResponse.Text(verdictJson)
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val session = DeepCheckSession(
            engine, allowlistDao, historyDao, null, officialSites, null,
            "Test message", "+1234567890",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            }
        )
        session.run()
        val finalVerdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull(finalVerdict)
    }

    @Test
    fun `max turns exceeded triggers fallback`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            LlmResponse.ToolCall("lookup_allowlist", """{}"""),
            LlmResponse.ToolCall("search_personal_db", """{"sender":"x","sms_prefix":"y"}"""),
            LlmResponse.ToolCall("offline_reputation_check", """{"urls":[]}"""),
            LlmResponse.ToolCall("whois_lookup", """{"domain":"x.com"}"""),
            LlmResponse.ToolCall("compare_official_site", """{"claimed_entity":"x","linked_domain":"y.com"}""")
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val session = DeepCheckSession(
            engine, allowlistDao, historyDao, null, officialSites, null,
            "Suspicious test with http://evil.xyz link",
            "Scammer",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            }
        )
        session.run()
        assertTrue(updates.any { it is DeepCheckUpdate.FinalVerdict })
    }

    @Test
    fun `cancellation stops the loop`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            LlmResponse.Error("should not complete")
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val session = DeepCheckSession(
            engine, allowlistDao, historyDao, null, officialSites, null,
            "Test", "Sender",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            }
        )
        session.cancel()
        session.run()
    }
}
