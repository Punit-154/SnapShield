package com.smssentry.ui.settings

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.os.Build
import android.provider.BlockedNumberContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BlockedNumber(
    val id: Long,
    val number: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    var blockedNumbers by remember { mutableStateOf<List<BlockedNumber>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var numberToDelete by remember { mutableStateOf<BlockedNumber?>(null) }

    // Load blocked numbers
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        try {
            blockedNumbers = loadBlockedNumbers(contentResolver)
        } catch (e: SecurityException) {
            errorMessage = "SMSentry must be the default SMS app to manage blocked numbers."
        } catch (e: Exception) {
            errorMessage = "Could not load blocked numbers: ${e.message}"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "🚫", fontSize = 22.sp)
                        Text(
                            text = "Blocked Numbers",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                blockedNumbers.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Text(
                            text = "No blocked numbers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Numbers you block will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(
                            items = blockedNumbers,
                            key = { it.id },
                        ) { blocked ->
                            BlockedNumberItem(
                                blockedNumber = blocked,
                                onDeleteClick = { numberToDelete = blocked },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────
    numberToDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { numberToDelete = null },
            title = { Text("Unblock number?") },
            text = {
                Text("Unblock ${toDelete.number}? You will receive messages from this number again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            contentResolver.delete(
                                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                                "${BlockedNumberContract.BlockedNumbers.COLUMN_ID} = ?",
                                arrayOf(toDelete.id.toString()),
                            )
                            blockedNumbers = blockedNumbers.filter { it.id != toDelete.id }
                        } catch (_: Exception) {
                            // Silently fail — possibly lost default SMS role
                        }
                        numberToDelete = null
                    },
                ) {
                    Text("Unblock", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { numberToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BlockedNumberItem(
    blockedNumber: BlockedNumber,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = blockedNumber.number,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Unblock",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private suspend fun loadBlockedNumbers(
    contentResolver: ContentResolver,
): List<BlockedNumber> = withContext(Dispatchers.IO) {
    val list = mutableListOf<BlockedNumber>()
    val cursor = contentResolver.query(
        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
        arrayOf(
            BlockedNumberContract.BlockedNumbers.COLUMN_ID,
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
        ),
        null,
        null,
        null,
    )
    cursor?.use {
        val idIdx = it.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
        val numberIdx = it.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
        while (it.moveToNext()) {
            list.add(
                BlockedNumber(
                    id = it.getLong(idIdx),
                    number = it.getString(numberIdx) ?: "Unknown",
                ),
            )
        }
    }
    list
}
