package com.smssentry.ui.settings

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.telephony.PhoneNumberUtils
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.content.ContentValues
import androidx.compose.ui.res.stringResource
import com.smssentry.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var showAddDialog by remember { mutableStateOf(false) }
    var newNumberText by remember { mutableStateOf("") }
    var addNumberError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Load blocked numbers
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        try {
            blockedNumbers = loadBlockedNumbers(contentResolver, context)
        } catch (e: SecurityException) {
            errorMessage = context.getString(R.string.error_default_sms_required)
        } catch (e: Exception) {
            errorMessage = "Could not load blocked numbers."
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isLoading && errorMessage == null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.block_number),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "🚫", fontSize = 22.sp)
                        Text(
                            text = stringResource(R.string.blocked_numbers),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
                            contentDescription = stringResource(R.string.error_icon_desc),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = errorMessage ?: "",
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
                            contentDescription = stringResource(R.string.no_blocked_numbers),
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Text(
                            text = stringResource(R.string.no_blocked_numbers),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.blocked_numbers_empty_subtitle),
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
            title = { Text(stringResource(R.string.unblock_title)) },
            text = {
                Text(stringResource(R.string.unblock_body, toDelete.number))
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
                        } catch (e: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.error_unblock_failed)
                                )
                            }
                        }
                        numberToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.unblock), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { numberToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── Add blocked number dialog ──────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newNumberText = ""
            },
            title = { Text(stringResource(R.string.block_number)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newNumberText,
                        onValueChange = {
                            newNumberText = it
                            addNumberError = null
                        },
                        label = { Text(stringResource(R.string.phone_number_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = addNumberError != null,
                        supportingText = addNumberError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rawInput = newNumberText.trim()
                        // Validate: normalize, check length and character set
                        val normalized = PhoneNumberUtils.normalizeNumber(rawInput) ?: ""
                        val isValid = normalized.isNotEmpty()
                                && normalized.length in 3..20
                                && normalized.matches(Regex("""^\+?[0-9]+$"""))
                        if (!isValid) {
                            addNumberError = context.getString(R.string.error_invalid_phone)
                        } else {
                            scope.launch {
                                try {
                                    val values = ContentValues().apply {
                                        put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, normalized)
                                    }
                                    withContext(Dispatchers.IO) {
                                        contentResolver.insert(
                                            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                                            values,
                                        )
                                    }
                                    blockedNumbers = loadBlockedNumbers(contentResolver, context)
                                    snackbarHostState.showSnackbar(context.getString(R.string.number_blocked, normalized))
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.error_block_failed)
                                    )
                                }
                            }
                            showAddDialog = false
                            newNumberText = ""
                            addNumberError = null
                        }
                    },
                    enabled = newNumberText.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.block_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        newNumberText = ""
                        addNumberError = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
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
                contentDescription = stringResource(R.string.blocked_icon_desc),
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
                    contentDescription = stringResource(R.string.unblock),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private suspend fun loadBlockedNumbers(
    contentResolver: ContentResolver,
    context: Context,
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
                    number = it.getString(numberIdx) ?: context.getString(R.string.unknown),
                ),
            )
        }
    }
    list
}
