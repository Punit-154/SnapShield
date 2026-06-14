package com.smssentry.ui.detail

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.R
import com.smssentry.data.model.DeepCheckVerdict
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.deepcheck.ui.DeepCheckTimeline
import com.smssentry.ui.components.*
import com.smssentry.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBackClick: () -> Unit,
    onNavigateToDownload: () -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel()
) {
    val message by viewModel.message.collectAsState()
    val investigationState by viewModel.investigationState.collectAsState()
    val showDownloadPrompt by viewModel.showDownloadPrompt.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val context = LocalContext.current

    var badgeVisible by remember { mutableStateOf(false) }

    val isInvestigating = investigationState.progress > 0 && investigationState.verdict == null
    val isModelReady = modelState == ModelRepository.State.READY

    // Pulsing animation for Deep Check running
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(message?.classification) {
        badgeVisible = false
        kotlinx.coroutines.delay(100)
        badgeVisible = true
    }

    if (showDownloadPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.onDownloadPromptDismissed() },
            title = { Text(stringResource(R.string.download_prompt_title)) },
            text = {
                Text(stringResource(R.string.download_prompt_desc))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDownloadPromptDismissed()
                    onNavigateToDownload()
                }) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDownloadPromptDismissed() }) {
                    Text(stringResource(R.string.use_rule_based))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    // Share button in top bar
                    message?.let { sms ->
                        IconButton(onClick = {
                            shareMessage(context, sms.sender, sms.text, investigationState.verdict, context.getString(R.string.share_via))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        message?.let { sms ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Message Card ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Sender avatar
                                val avatarColor = when (sms.classification?.label?.uppercase()) {
                                    "SCAM" -> ScamRed
                                    "SUSPICIOUS" -> SuspiciousOrange
                                    "SAFE" -> SafeGreen
                                    else -> LowGray
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(avatarColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sms.sender.firstOrNull()?.uppercase() ?: "?",
                                        color = avatarColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = sms.sender,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                                            .format(Date(sms.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = sms.text,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp
                        )
                    }
                }

                // ── Classification Card ──
                sms.classification?.let { classification ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                    text = stringResource(R.string.classification),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )

                            AnimatedVisibility(
                                visible = badgeVisible,
                                enter = scaleIn(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) + fadeIn(),
                                exit = fadeOut()
                            ) {
                                ShieldBadge(
                                    label = classification.label,
                                    riskScore = classification.riskScore,
                                    animated = true
                                )
                            }

                            RiskScoreBar(riskScore = classification.riskScore)

                            Text(
                                text = classification.reasoning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // ── Privacy Indicator ──
                PrivacyIndicator()

                // ── Deep Check Section ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                            Text(
                                text = stringResource(R.string.ai_deep_analysis),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )

                        // Dynamic button based on model state
                        when (modelState) {
                            ModelRepository.State.IDLE, ModelRepository.State.FAILED -> {
                                Button(
                                    onClick = onNavigateToDownload,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.download), Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.download_ai_model),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.download_ai_model_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            ModelRepository.State.DOWNLOADING -> {
                                Button(
                                    onClick = onNavigateToDownload,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.detail_downloading))
                                }
                            }

                            ModelRepository.State.VERIFYING, ModelRepository.State.LOADING -> {
                                Button(
                                    onClick = { /* Disabled */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    enabled = false
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.initializing_ai_engine))
                                }
                            }

                            ModelRepository.State.READY -> {
                                Button(
                                    onClick = { viewModel.startDeepCheck() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isInvestigating) Modifier.scale(pulseAnimation)
                                            else Modifier
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInvestigating) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                    enabled = !isInvestigating
                                ) {
                                    if (isInvestigating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.analyzing),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.analyze), Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (investigationState.verdict != null) {
                                                stringResource(R.string.run_again)
                                            } else {
                                                stringResource(R.string.run_deep_analysis)
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // ── Deep Check Timeline (when results exist) ──
                if (investigationState.progress > 0 || investigationState.verdict != null || investigationState.error != null) {
                    DeepCheckTimeline(
                        state = investigationState,
                        onCancel = { viewModel.cancelDeepCheck() },
                        onShareClick = investigationState.verdict?.let { verdict ->
                            { shareVerdict(context, sms.sender, verdict, context.getString(R.string.share_verdict)) }
                        }
                    )
                }

                // ── Feedback Section ──
                val feedbackState by viewModel.feedbackState.collectAsState()
                if (investigationState.verdict != null && feedbackState != DetailViewModel.FeedbackState.Submitted) {
                    FeedbackSection(
                        onMarkSafe = { viewModel.submitFeedback("SAFE") },
                        onMarkScam = { viewModel.submitFeedback("SCAM") }
                    )
                } else if (feedbackState == DetailViewModel.FeedbackState.Submitted) {
                    Text(
                        text = stringResource(R.string.feedback_submitted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        } ?: run {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.loading_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackSection(
    onMarkSafe: () -> Unit,
    onMarkScam: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.feedback_question),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onMarkSafe,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SafeGreen
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.horizontalGradient(listOf(SafeGreen, SafeGreen))
                )
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.feedback_mark_safe),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.feedback_mark_safe), fontWeight = FontWeight.Medium)
            }
            OutlinedButton(
                onClick = onMarkScam,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ScamRed
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.horizontalGradient(listOf(ScamRed, ScamRed))
                )
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.feedback_report_scam),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.feedback_report_scam), fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun shareMessage(context: Context, sender: String, text: String, verdict: DeepCheckVerdict?, chooserTitle: String) {
    val shareText = buildString {
        appendLine("SMS from: $sender")
        appendLine(text)
        if (verdict != null) {
            appendLine()
            appendLine("SMSentry Analysis: ${if (verdict.isScam) "⚠️ SCAM DETECTED" else "✅ SAFE"}")
            appendLine(verdict.summary)
        }
        appendLine()
        appendLine("— Analyzed by SMSentry")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun shareVerdict(context: Context, sender: String, verdict: DeepCheckVerdict, chooserTitle: String) {
    val shareText = buildString {
        appendLine("SMSentry Deep Check Result")
        appendLine("━━━━━━━━━━━━━━━━━━")
        appendLine("Sender: $sender")
        appendLine("Verdict: ${if (verdict.isScam) "🛑 SCAM DETECTED" else "🛡️ MESSAGE SAFE"}")
        verdict.threatType?.let { appendLine("Threat: ${it.replace("_", " ")}") }
        appendLine()
        appendLine(verdict.summary)
        if (verdict.recommendedActions.isNotEmpty()) {
            appendLine()
            appendLine("Actions:")
            verdict.recommendedActions.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine("— Analyzed by SMSentry")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
