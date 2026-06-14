package com.smssentry.ui.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.R
import com.smssentry.deepcheck.data.ModelRepository
import com.smssentry.deepcheck.ui.DeepCheckTimeline
import com.smssentry.ui.components.*
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

    var badgeVisible by remember { mutableStateOf(false) }

    val isInvestigating = investigationState.progress > 0 && investigationState.verdict == null
    val canStartDeepCheck = !isInvestigating && investigationState.verdict == null
    val isModelReady = modelState == ModelRepository.State.READY

    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sms.sender,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                                    .format(Date(sms.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = sms.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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

                PrivacyIndicator()

                // Dynamic Deep Check button based on model state
                when (modelState) {
                    ModelRepository.State.IDLE, ModelRepository.State.FAILED -> {
                        // No model downloaded
                        Button(
                            onClick = onNavigateToDownload,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download AI for Deep Analysis (~2.7 GB)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Get complete protection against sophisticated scams.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    ModelRepository.State.DOWNLOADING -> {
                        // Download in progress
                        Button(
                            onClick = onNavigateToDownload,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                Text("Downloading...")
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                    
                    ModelRepository.State.VERIFYING, ModelRepository.State.LOADING -> {
                        // Model loading (engine.init)
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
                            Text("Initializing AI Engine...")
                        }
                    }
                    
                    ModelRepository.State.READY -> {
                        // Model ready - show Immediate Deep Check
                        Button(
                            onClick = { viewModel.startDeepCheck() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (investigationState.verdict != null) {
                                    "Check Another Message"
                                } else {
                                    "⚡ Run Deep Analysis"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                if (investigationState.progress > 0 || investigationState.verdict != null || investigationState.error != null) {
                    DeepCheckTimeline(
                        state = investigationState,
                        onCancel = { viewModel.cancelDeepCheck() }
                    )
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
