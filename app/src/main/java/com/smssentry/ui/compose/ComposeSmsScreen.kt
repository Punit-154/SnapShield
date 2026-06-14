package com.smssentry.ui.compose

import androidx.compose.ui.res.stringResource
import com.smssentry.R
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/** Single-SMS limit for 7-bit GSM encoding. */
private const val SMS_SINGLE_LIMIT = 160

/** Per-segment limit once the message is concatenated (multi-part). */
private const val SMS_MULTI_LIMIT = 153

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSmsScreen(
    onBackClick: () -> Unit,
    viewModel: ComposeSmsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // ── Contact picker ─────────────────────────────────────────────────
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
    ) { uri ->
        if (uri != null) {
            try {
                // Step 1: Get the contact ID from the contact URI
                val contactCursor = context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts._ID),
                    null, null, null,
                )
                val contactId = contactCursor?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }

                if (contactId != null) {
                    // Step 2: Query for the contact's phone number using the contact ID
                    val phoneCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null,
                    )
                    phoneCursor?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val number = cursor.getString(0)
                            if (!number.isNullOrBlank()) {
                                viewModel.onRecipientChanged(number.trim())
                                messageFocusRequester.requestFocus()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Permission denied or content resolver failure — ignore
            }
        }
    }

    // Handle send result
    LaunchedEffect(state.sendResult) {
        when (state.sendResult) {
            SendResult.SUCCESS -> {
                snackbarHostState.showSnackbar("Message sent")
                viewModel.clearSendResult()
                onBackClick()
            }
            SendResult.FAILURE -> {
                snackbarHostState.showSnackbar(context.getString(R.string.compose_failed))
                viewModel.clearSendResult()
            }
            SendResult.EMPTY_FIELDS -> {
                snackbarHostState.showSnackbar(context.getString(R.string.compose_empty_fields))
                viewModel.clearSendResult()
            }
            null -> { /* no-op */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.compose_sms),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recipient field with contact picker
            OutlinedTextField(
                value = state.recipient,
                onValueChange = { viewModel.onRecipientChanged(it) },
                label = { Text(stringResource(R.string.compose_to)) },
                placeholder = { Text(stringResource(R.string.compose_phone_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { messageFocusRequester.requestFocus() }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { contactPickerLauncher.launch(null) },
                        enabled = !state.isSending,
                    ) {
                        Icon(
                            Icons.Filled.Contacts,
                            contentDescription = "Pick contact",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isSending
            )

            // Message field
            OutlinedTextField(
                value = state.message,
                onValueChange = { viewModel.onMessageChanged(it) },
                label = { Text(stringResource(R.string.compose_message)) },
                placeholder = { Text(stringResource(R.string.compose_type_message)) },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        keyboardController?.hide()
                        viewModel.sendSms()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .focusRequester(messageFocusRequester),
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isSending,
            )

            // ── Character counter with segment info ────────────────────
            val charCount = state.message.length
            val smsSegments = when {
                charCount == 0 -> 0
                charCount <= SMS_SINGLE_LIMIT -> 1
                else -> ((charCount - 1) / SMS_MULTI_LIMIT) + 1
            }
            val charsInCurrentSegment = when {
                charCount == 0 -> 0
                charCount <= SMS_SINGLE_LIMIT -> charCount
                else -> {
                    val remainder = (charCount - SMS_SINGLE_LIMIT) % SMS_MULTI_LIMIT
                    if (remainder == 0) SMS_MULTI_LIMIT else remainder
                }
            }
            val currentSegmentLimit = if (smsSegments <= 1) SMS_SINGLE_LIMIT else SMS_MULTI_LIMIT
            val segmentProgress = if (currentSegmentLimit > 0) {
                charsInCurrentSegment.toFloat() / currentSegmentLimit
            } else 0f

            val counterColor = when {
                smsSegments >= 3 -> MaterialTheme.colorScheme.error
                charCount >= 140 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }

            AnimatedVisibility(visible = state.message.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "$charCount character${if (charCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = counterColor,
                        )
                        Text(
                            text = "$smsSegments SMS segment${if (smsSegments != 1) "s" else ""} • $charsInCurrentSegment/$currentSegmentLimit",
                            style = MaterialTheme.typography.labelSmall,
                            color = counterColor,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { segmentProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = counterColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Send button
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.sendSms()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isSending && state.recipient.isNotBlank() && state.message.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.compose_sending))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.compose_send),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

