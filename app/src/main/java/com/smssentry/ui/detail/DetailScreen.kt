package com.smssentry.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.data.model.EvidenceItem
import java.text.SimpleDateFormat
import java.util.*

// ── Dark theme palette ──
private val BG = Color(0xFF0E0E0E)
private val SURFACE = Color(0xFF1A1A1A)
private val SURFACE2 = Color(0xFF242424)
private val DIVIDER = Color(0xFF2A2A2A)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)
private val TEXT_SECONDARY = Color(0xFF888888)
private val TEXT_HINT = Color(0xFF555555)
private val SCAM = Color(0xFFE53935)
private val WARN = Color(0xFFFB8C00)
private val SAFE = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val message by viewModel.message.collectAsState()
    val investigationState by viewModel.investigationState.collectAsState()

    val classification = message?.classification
    val accentColor = when (classification?.label) {
        "SCAM" -> SCAM
        "SUSPICIOUS" -> WARN
        "SAFE" -> SAFE
        else -> TEXT_SECONDARY
    }

    Scaffold(
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Message detail",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = TEXT_PRIMARY
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TEXT_PRIMARY
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SURFACE
                )
            )
        }
    ) { padding ->
        message?.let { sms ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {

                // ── Sender header ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SURFACE)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sms.sender.filter { it.isLetter() }.take(2)
                                .uppercase().ifEmpty { "#" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                    }

                    Text(
                        text = sms.sender,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TEXT_PRIMARY
                    )

                    Text(
                        text = SimpleDateFormat(
                            "MMM dd, yyyy • HH:mm",
                            Locale.getDefault()
                        ).format(Date(sms.timestamp)),
                        fontSize = 12.sp,
                        color = TEXT_HINT
                    )

                    // Classification badge
                    classification?.let {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Text(
                                text = when (it.label) {
                                    "SCAM" -> "SCAM"
                                    "SUSPICIOUS" -> "SUSPICIOUS"
                                    else -> "SAFE"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor
                            )
                            Text(
                                text = "·",
                                color = TEXT_HINT,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Risk ${it.riskScore}/100",
                                fontSize = 13.sp,
                                color = TEXT_SECONDARY
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Message body ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SURFACE)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "MESSAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TEXT_HINT,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = sms.text,
                        fontSize = 15.sp,
                        color = TEXT_PRIMARY,
                        lineHeight = 23.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Risk analysis ──
                classification?.let { cls ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SURFACE)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "RISK ANALYSIS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TEXT_HINT,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Risk score",
                                fontSize = 14.sp,
                                color = TEXT_SECONDARY
                            )
                            Text(
                                text = "${cls.riskScore}/100",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor
                            )
                        }

                        // Risk bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(SURFACE2)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(cls.riskScore / 100f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accentColor)
                            )
                        }

                        Text(
                            text = cls.reasoning,
                            fontSize = 13.sp,
                            color = TEXT_SECONDARY,
                            lineHeight = 20.sp
                        )

                        HorizontalDivider(color = DIVIDER, thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Confidence",
                                fontSize = 13.sp,
                                color = TEXT_HINT
                            )
                            Text(
                                text = "${(cls.confidence * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TEXT_SECONDARY
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Privacy note ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SURFACE)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🛡️", fontSize = 14.sp)
                    Text(
                        text = "Analysed on-device. No data leaves your phone.",
                        fontSize = 13.sp,
                        color = TEXT_HINT
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Deep check ──
                val state = investigationState
                when {
                    state.progress == 0 && state.verdict == null && state.error == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startDeepCheck() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SURFACE,
                                    contentColor = TEXT_PRIMARY
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    width = 0.5.dp
                                )
                            ) {
                                Text(
                                    "Run deep check",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    state.verdict == null && state.error == null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SURFACE)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "INVESTIGATING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TEXT_HINT,
                                letterSpacing = 1.sp
                            )

                            state.currentStep?.let {
                                Text(
                                    text = it,
                                    fontSize = 14.sp,
                                    color = TEXT_SECONDARY
                                )
                            }

                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = TEXT_PRIMARY,
                                trackColor = SURFACE2
                            )

                            Text(
                                text = "${state.progress}%",
                                fontSize = 12.sp,
                                color = TEXT_HINT
                            )

                            if (state.evidence.isNotEmpty()) {
                                HorizontalDivider(color = DIVIDER, thickness = 0.5.dp)
                                Text(
                                    text = "EVIDENCE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TEXT_HINT,
                                    letterSpacing = 1.sp
                                )
                                state.evidence.forEach { EvidenceRow(it) }
                            }

                            TextButton(
                                onClick = { viewModel.cancelDeepCheck() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    "Cancel",
                                    fontSize = 13.sp,
                                    color = TEXT_HINT
                                )
                            }
                        }
                    }

                    state.verdict != null -> {
                        VerdictSection(
                            verdict = state.verdict!!,
                            evidence = state.evidence
                        )
                    }

                    state.error != null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SCAM.copy(alpha = 0.1f))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Text(
                                text = state.error!!,
                                color = SCAM,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = TEXT_PRIMARY,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun EvidenceRow(evidence: EvidenceItem) {
    val color = when (evidence.severity) {
        "CRITICAL" -> SCAM
        "HIGH" -> WARN
        "MEDIUM" -> Color(0xFFD4A017)
        else -> SAFE
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = evidence.source,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                letterSpacing = 0.5.sp
            )
            Text(
                text = evidence.detail,
                fontSize = 13.sp,
                color = TEXT_SECONDARY,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun VerdictSection(verdict: DeepCheckVerdict, evidence: List<EvidenceItem>) {
    val isScam = verdict.isScam
    val color = if (isScam) SCAM else SAFE

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Verdict banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.08f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isScam) "🛑" else "✅",
                fontSize = 32.sp
            )
            Text(
                text = if (isScam) "Scam detected" else "Message safe",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
            verdict.threatType?.let {
                Text(
                    text = it.replace("_", " "),
                    fontSize = 12.sp,
                    color = color.copy(alpha = 0.6f)
                )
            }
            Text(
                text = verdict.summary,
                fontSize = 13.sp,
                color = TEXT_SECONDARY,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Recommended actions
        if (verdict.recommendedActions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SURFACE)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "RECOMMENDED ACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TEXT_HINT,
                    letterSpacing = 1.sp
                )
                verdict.recommendedActions.forEach { action ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = action,
                            fontSize = 14.sp,
                            color = TEXT_SECONDARY
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Evidence
        if (evidence.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SURFACE)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "EVIDENCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TEXT_HINT,
                    letterSpacing = 1.sp
                )
                evidence.forEach { EvidenceRow(it) }
            }
        }
    }
}