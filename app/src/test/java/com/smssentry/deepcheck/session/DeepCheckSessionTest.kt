package com.smssentry.deepcheck.session

import com.smssentry.data.model.DeepCheckUpdate
import com.smssentry.deepcheck.data.AllowlistEntry
import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.data.TestDatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepCheckSessionTest : TestDatabaseProvider() {

    private val officialSites = OfficialSitesRepository(
        mapOf("hsbc" to "hsbc.co.in", "sbi" to "onlinesbi.sbi")
    )
    
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    override fun setupDatabase() {
        super.setupDatabase()
    }

    @Test
    fun `fast path short-circuit does not invoke LLM`() = runBlocking {
        allowlistDao.insert(AllowlistEntry("+1234567890", "sender", false))
        val engine = MockLlmEngine(listOf(
            "__ERROR__"
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = DeepCheckSession(
            context, engine, allowlistDao, historyDao, null, officialSites, null,
            "Hello", "+1234567890",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            testScope
        )
        session.run()
        assertTrue(updates.last() is DeepCheckUpdate.FinalVerdict)
        val verdict = (updates.last() as DeepCheckUpdate.FinalVerdict).verdict
        assertFalse(verdict.isScam)
    }

    @Test
    fun `model timeout triggers fallback verdict`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            "__TIMEOUT__"
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = DeepCheckSession(
            context, engine, allowlistDao, historyDao, null, officialSites, null,
            "Suspicious message with http://evil.xyz link",
            "Scammer",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            testScope
        )
        session.run()
        assertTrue(updates.any { it is DeepCheckUpdate.FinalVerdict })
    }

    @Test
    fun `model error triggers fallback`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            "__ERROR__"
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = DeepCheckSession(
            context, engine, allowlistDao, historyDao, null, officialSites, null,
            "Click http://scam.xyz/verify now!",
            "Unknown",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            testScope
        )
        session.run()
        assertTrue(updates.any { it is DeepCheckUpdate.FinalVerdict })
    }

    @Test
    fun `happy path with tool call then verdict`() = runBlocking {
        val verdictText = "<<<VERDICT:SAFE,0.95,safe>>>\nDomain is verified. This is a safe message."
        val engine = MockLlmEngine(listOf(
            "ACTION: whois|example.com",
            verdictText
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = DeepCheckSession(
            context, engine, allowlistDao, historyDao, null, officialSites, null,
            "Test message", "+1234567890",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            testScope
        )
        session.run()
        val finalVerdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull(finalVerdict)
        assertFalse(finalVerdict!!.verdict.isScam)
        assertEquals("Domain is verified. This is a safe message.", finalVerdict.verdict.educationalExplanation)
    }

    @Test
    fun `max turns exceeded triggers fallback`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            "ACTION: whois|1.com",
            "ACTION: whois|2.com",
            "ACTION: whois|3.com",
            "ACTION: whois|4.com",
            "ACTION: whois|5.com",
            "ACTION: whois|6.com"
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = DeepCheckSession(
            context, engine, allowlistDao, historyDao, null, officialSites, null,
            "Suspicious test with http://evil.xyz link",
            "Scammer",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            testScope
        )
        session.run()
        assertTrue(updates.any { it is DeepCheckUpdate.FinalVerdict })
    }

    @Test
    fun `cancellation stops the loop`() = runBlocking {
        val engine = MockLlmEngine(listOf(
            "__TIMEOUT__"
        ))
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = DeepCheckSession(
            context, engine, allowlistDao, historyDao, null, officialSites, null,
            "Test", "Sender",
            object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            testScope
        )
        session.cancel()
        session.run()
    }
}
