package com.smssentry.deepcheck.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smssentry.R
import com.smssentry.deepcheck.data.ModelRepository
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onBackClick: () -> Unit,
    viewModel: ModelDownloadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    
    LaunchedEffect(state) {
        if (state == ModelRepository.State.READY) {
            delay(1500)
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_download_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelDownload()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.setup_deep_check),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.downloading_model_info),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (state) {
                ModelRepository.State.IDLE -> {
                    IdleContent(
                        wifiOnly = wifiOnly,
                        onWifiOnlyToggle = { viewModel.toggleWifiOnly() },
                        onStartDownload = { viewModel.startDownload() }
                    )
                }
                ModelRepository.State.DOWNLOADING -> {
                    DownloadingContent(
                        progress = progress,
                        onCancel = { viewModel.cancelDownload() }
                    )
                }
ModelRepository.State.VERIFYING -> {
    CancelableStatusContent(
        text = stringResource(R.string.verifying_download),
        onCancel = {
            viewModel.cancelDownload()
            onBackClick()
        }
    )
}
ModelRepository.State.LOADING -> {
    CancelableStatusContent(
        text = stringResource(R.string.step_loading_model),
        onCancel = {
            viewModel.cancelDownload()
            onBackClick()
        }
    )
}
                ModelRepository.State.READY -> {
                    CompleteContent(onContinue = onBackClick)
                }
                ModelRepository.State.FAILED -> {
                    FailedContent(
                        error = error,
                        onRetry = { viewModel.retryDownload() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    wifiOnly: Boolean,
    onWifiOnlyToggle: () -> Unit,
    onStartDownload: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = wifiOnly,
                onCheckedChange = { onWifiOnlyToggle() }
            )
            Text(
                text = stringResource(R.string.wifi_only),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onStartDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.start_download),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun DownloadingContent(
    progress: Float,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(180.dp),
                strokeWidth = 12.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun StatusContent(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(80.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CancelableStatusContent(text: String, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(80.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun CompleteContent(onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.download_complete),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.continue_text),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun FailedContent(
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        error?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(R.string.retry_download),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
