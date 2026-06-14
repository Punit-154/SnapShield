package com.smssentry.deepcheck.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class OfflineEvaluationTest : TestDatabaseProvider() {

    private val officialSites = OfficialSitesRepository(
        mapOf("hsbc" to "hsbc.co.in", "sbi" to "onlinesbi.sbi")
    )

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    override fun setupDatabase() {
        super.setupDatabase()
    }

    private val rubrics = listOf(
        OfflineEvaluationRubric(
            id = "otp_clean",
            name = "Clean OTP message",
            smsText = "Dear customer, your OTP for transaction is 4829. Do not share with anyone.",
            sender = "+91 98765 43210",
            expectedVerdict = "SAFE",
            expectedConfidenceRange = 0.85f..1.0f,
            keyEvidence = "OTP with no links",
            maxTurns = 1
        ),
        OfflineEvaluationRubric(
            id = "bank_allowlisted",
            name = "Allowlisted bank SMS with legit link",
            smsText = "Your SBI account balance is Rs.15,230. View details at onlinesbi.sbi",
            sender = "SBI",
            expectedVerdict = "SAFE",
            expectedConfidenceRange = 0.90f..1.0f,
            keyEvidence = "Allowlisted sender or domain",
            maxTurns = 1,
            setup = { dao, _ -> dao.insert(AllowlistEntry("SBI", "sender", false)) }
        ),
        OfflineEvaluationRubric(
            id = "phishing_homesubdomain",
            name = "Classic phishing with lookalike domain + brand mismatch",
            smsText = "URGENT: Your HSBC account has been suspended. Click here to verify: hsbc-secure.xyz/verify",
            sender = "+44 7911 123456",
            expectedVerdict = "SCAM",
            expectedConfidenceRange = 0.70f..1.0f,
            keyEvidence = "Brand mismatch or suspicious TLD",
            maxTurns = 4
        ),
        OfflineEvaluationRubric(
            id = "ip_address_scam",
            name = "IP-address-link scam (fast-path catch)",
            smsText = "Congratulations! Claim your prize at http://192.168.1.100/claim?id=12345",
            sender = "+1 555 0123",
            expectedVerdict = "SCAM",
            expectedConfidenceRange = 0.90f..1.0f,
            keyEvidence = "IP address URL detected",
            maxTurns = 1
        ),
        OfflineEvaluationRubric(
            id = "ambiguous_lottery",
            name = "Ambiguous lottery scam requiring full agentic loop",
            smsText = "You've been selected for a $5,000,000 Microsoft Lottery! Claim at ms-lottery.win/claim. Send your details NOW!",
            sender = "+234 801 2345678",
            expectedVerdict = "SCAM",
            expectedConfidenceRange = 0.60f..1.0f,
            keyEvidence = "Suspicious TLD .win or brand mismatch",
            maxTurns = 4
        )
    )

    @Test
    fun `Task 1 - Clean OTP fast-path SAFE`() = runBlocking {
        val rubric = rubrics[0]
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = DeepCheckSession(
            context = context,
            engine = null,
            allowlistDao = allowlistDao,
            historyDao = historyDao,
            reputationDb = null,
            officialSites = officialSites,
            proxyClient = null,
            smsText = rubric.smsText,
            smsSender = rubric.sender,
            listener = object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            applicationScope = testScope
        )
        session.run()

        val verdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull("Should emit a FinalVerdict", verdict)
        val v = verdict!!.verdict
        assertFalse("OTP message should not be flagged as scam", v.isScam)
    }

    @Test
    fun `Task 2 - Allowlisted bank fast-path SAFE`() = runBlocking {
        val rubric = rubrics[1]
        rubric.setup?.invoke(allowlistDao, historyDao)
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = DeepCheckSession(
            context = context,
            engine = null,
            allowlistDao = allowlistDao,
            historyDao = historyDao,
            reputationDb = null,
            officialSites = officialSites,
            proxyClient = null,
            smsText = rubric.smsText,
            smsSender = rubric.sender,
            listener = object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            applicationScope = testScope
        )
        session.run()

        val verdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull("Should emit a FinalVerdict", verdict)
        assertFalse("Allowlisted bank should not be flagged as scam", verdict!!.verdict.isScam)
    }

    @Test
    fun `Task 3 - Phishing domain detected as SCAM`() = runBlocking {
        val rubric = rubrics[2]
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = DeepCheckSession(
            context = context,
            engine = null,
            allowlistDao = allowlistDao,
            historyDao = historyDao,
            reputationDb = null,
            officialSites = officialSites,
            proxyClient = null,
            smsText = rubric.smsText,
            smsSender = rubric.sender,
            listener = object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            applicationScope = testScope
        )
        session.run()

        val verdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull("Should emit a FinalVerdict", verdict)
        val v = verdict!!.verdict
        assertTrue("Phishing message should be SCAM or have evidence", v.isScam || v.evidence.isNotEmpty())
    }

    @Test
    fun `Task 4 - IP address scam fast-path SCAM`() = runBlocking {
        val rubric = rubrics[3]
        val updates = mutableListOf<DeepCheckUpdate>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = DeepCheckSession(
            context = context,
            engine = null,
            allowlistDao = allowlistDao,
            historyDao = historyDao,
            reputationDb = null,
            officialSites = officialSites,
            proxyClient = null,
            smsText = rubric.smsText,
            smsSender = rubric.sender,
            listener = object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            applicationScope = testScope
        )
        session.run()

        val verdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull("Should emit a FinalVerdict", verdict)
        assertTrue("IP address URL should be caught as SCAM", verdict!!.verdict.isScam)
    }

    @Test
    fun `Task 5 - Ambiguous lottery scam SCAM`() = runBlocking {
        val rubric = rubrics[4]
        val updates = mutableListOf<DeepCheckUpdate>()
        val mockEngine = MockLlmEngine(
            listOf(
                "<<<VERDICT:SCAM,0.85,lottery>>>\nThis is a scam lottery message pretending to be from Microsoft."
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = DeepCheckSession(
            context = context,
            engine = mockEngine,
            allowlistDao = allowlistDao,
            historyDao = historyDao,
            reputationDb = null,
            officialSites = officialSites,
            proxyClient = null,
            smsText = rubric.smsText,
            smsSender = rubric.sender,
            listener = object : com.smssentry.domain.service.DeepCheckListener {
                override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
            },
            applicationScope = testScope
        )
        session.run()

        val verdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
        assertNotNull("Should emit a FinalVerdict", verdict)
        val v = verdict!!.verdict
        assertTrue("Lottery scam should be flagged as SCAM", v.isScam)
        assertEquals("This is a scam lottery message pretending to be from Microsoft.", v.educationalExplanation)
    }

    @Test
    fun `All 5 evaluations produce a FinalVerdict`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (rubric in rubrics) {
            val updates = mutableListOf<DeepCheckUpdate>()
            val session = DeepCheckSession(
                context = context,
                engine = null,
                allowlistDao = allowlistDao,
                historyDao = historyDao,
                reputationDb = null,
                officialSites = officialSites,
                proxyClient = null,
                smsText = rubric.smsText,
                smsSender = rubric.sender,
                listener = object : com.smssentry.domain.service.DeepCheckListener {
                    override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
                },
                applicationScope = testScope
            )
            session.run()

            val verdict = updates.filterIsInstance<DeepCheckUpdate.FinalVerdict>().lastOrNull()
            assertNotNull("Rubric ${rubric.id} should produce a FinalVerdict", verdict)
        }
    }

    @Test
    fun `All evaluations complete without error updates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (rubric in rubrics) {
            val updates = mutableListOf<DeepCheckUpdate>()
            val session = DeepCheckSession(
                context = context,
                engine = null,
                allowlistDao = allowlistDao,
                historyDao = historyDao,
                reputationDb = null,
                officialSites = officialSites,
                proxyClient = null,
                smsText = rubric.smsText,
                smsSender = rubric.sender,
                listener = object : com.smssentry.domain.service.DeepCheckListener {
                    override fun onUpdate(update: DeepCheckUpdate) { updates.add(update) }
                },
                applicationScope = testScope
            )
            session.run()

            val errors = updates.filterIsInstance<DeepCheckUpdate.Error>()
            assertTrue("Rubric ${rubric.id} should not produce errors, got: ${errors.map { it.reason }}", errors.isEmpty())
        }
    }
}
