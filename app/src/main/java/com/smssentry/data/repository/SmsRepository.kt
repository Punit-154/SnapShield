package com.smssentry.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.smssentry.data.model.Conversation
import com.smssentry.data.model.SmsMessage
import com.smssentry.data.util.ContactResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central data-access layer for SMS messages. All reads and writes go through the
 * Android [Telephony.Sms] ContentProvider.
 *
 * ## Design decisions
 *
 * - **All public methods are `suspend` + `Dispatchers.IO`**: ContentProvider queries
 *   can be slow on devices with 100K+ messages, so everything runs off the main thread.
 *
 * - **Defensive column-index checks**: Some OEM ROMs strip columns from the SMS provider.
 *   Every cursor access checks `getColumnIndex() != -1` before reading. If a required
 *   column is missing, the method returns an empty list / null rather than crashing.
 *
 * - **LIMIT clauses on raw queries**: To avoid OOM on large inboxes, conversation and
 *   search queries are bounded. The heuristic `limit * 20` for [getConversations]
 *   assumes ~20 messages per thread on average.
 *
 * - **No PII logging**: Only thread IDs and message IDs are logged, never phone numbers
 *   or message bodies (except in DEBUG builds for sendSms confirmation).
 *
 * @see ConversationListViewModel which is the primary consumer of this repository
 */
@Singleton
class SmsRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val context: Context,
    private val contactResolver: ContactResolver,
) {

    companion object {
        private const val TAG = "SmsRepository"
    }

    // ── Conversations ────────────────────────────────────────────────────

    /**
     * Loads the most recent [limit] conversation threads from the SMS provider.
     *
     * Implementation: queries raw messages sorted by date DESC with a bounded row count,
     * then groups by thread_id using a [LinkedHashMap] to preserve insertion order
     * (newest thread first). Each thread becomes a [Conversation] with aggregate
     * unread count and total message count.
     */
    suspend fun getConversations(limit: Int = 100): List<Conversation> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) return@withContext emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )

        // Limit the raw query to avoid OOM on devices with 100K+ messages.
        // We fetch up to limit * 20 rows (heuristic: ~20 msgs/thread average)
        // to build accurate thread info while bounding memory usage.
        val queryLimit = limit * 20
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT $queryLimit",
        )

        // Collect all messages, grouped by thread_id. Because we sort by date DESC
        // the first message we encounter for a given thread is the latest one.
        data class ThreadBucket(
            val address: String,
            val lastBody: String,
            val lastTimestamp: Long,
            var totalCount: Int = 0,
            var unreadCount: Int = 0,
        )

        val threadMap = LinkedHashMap<Long, ThreadBucket>()

        cursor?.use {
            val threadIdIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIdx  = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx     = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx     = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx     = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIdx     = it.getColumnIndex(Telephony.Sms.READ)

            if (threadIdIdx == -1 || addressIdx == -1 || dateIdx == -1) {
                return@withContext emptyList()
            }

            while (it.moveToNext()) {
                val threadId = it.getLong(threadIdIdx)
                val address  = it.getString(addressIdx) ?: "Unknown"
                val body     = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                val date     = it.getLong(dateIdx)
                val type     = if (typeIdx != -1) it.getInt(typeIdx) else 0
                val read     = if (readIdx != -1) it.getInt(readIdx) else 0

                val bucket = threadMap[threadId]
                if (bucket == null) {
                    threadMap[threadId] = ThreadBucket(
                        address = address,
                        lastBody = body,
                        lastTimestamp = date,
                        totalCount = 1,
                        unreadCount = if (read == 0 && type == Telephony.Sms.MESSAGE_TYPE_INBOX) 1 else 0,
                    )
                } else {
                    bucket.totalCount++
                    if (read == 0 && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        bucket.unreadCount++
                    }
                }
            }
        }

        // Convert to Conversation list, already ordered by latest timestamp (LinkedHashMap preserves insertion order)
        threadMap.entries.take(limit).map { (threadId, bucket) ->
            val contactInfo = contactResolver.resolve(bucket.address)
            Conversation(
                threadId = threadId,
                address = bucket.address,
                displayName = contactInfo.displayName,
                photoUri = contactInfo.photoUri,
                lastMessage = bucket.lastBody,
                lastTimestamp = bucket.lastTimestamp,
                unreadCount = bucket.unreadCount,
                messageCount = bucket.totalCount,
            )
        }
    }

    // ── Thread messages ──────────────────────────────────────────────────

    /**
     * Loads up to [limit] messages for a given thread, paginated by [beforeTimestamp].
     * Returns messages in oldest-first order (reversed from the DESC query).
     * The caller passes the oldest visible message's timestamp as [beforeTimestamp]
     * to load the next page.
     */
    suspend fun getThreadMessages(
        threadId: Long,
        limit: Int = 50,
        beforeTimestamp: Long = Long.MAX_VALUE,
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) return@withContext emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.THREAD_ID,
        )

        val messages = mutableListOf<SmsMessage>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} < ?",
            arrayOf(threadId.toString(), beforeTimestamp.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )

        cursor?.use {
            val idIdx       = it.getColumnIndex(Telephony.Sms._ID)
            val addressIdx  = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx     = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx     = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx     = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIdx     = it.getColumnIndex(Telephony.Sms.READ)
            val threadIdx   = it.getColumnIndex(Telephony.Sms.THREAD_ID)

            if (idIdx == -1 || addressIdx == -1 || bodyIdx == -1 || dateIdx == -1 || typeIdx == -1 || threadIdx == -1) {
                return@withContext emptyList()
            }

            while (it.moveToNext()) {
                val id      = it.getString(idIdx) ?: continue
                val address = it.getString(addressIdx) ?: "Unknown"
                val body    = it.getString(bodyIdx)?.takeIf { b -> b.isNotBlank() } ?: continue
                val date    = it.getLong(dateIdx)
                val type    = it.getInt(typeIdx)
                val read    = if (readIdx != -1) it.getInt(readIdx) else 0
                val tid     = it.getLong(threadIdx)

                messages.add(
                    SmsMessage(
                        id = id,
                        threadId = tid,
                        sender = address,
                        text = body,
                        timestamp = date,
                        isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT,
                        isRead = read == 1,
                    )
                )
            }
        }

        // Query fetched newest-first; reverse so callers get oldest-first order
        messages.reversed()
    }

    // ── Mark thread as read ──────────────────────────────────────────────

    /** Marks all unread messages in [threadId] as read via a bulk ContentProvider update. */
    suspend fun markThreadAsRead(threadId: Long): Unit = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }
        contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
        )
    }

    // ── Delete conversation ──────────────────────────────────────────────

    suspend fun deleteConversation(threadId: Long): Unit = withContext(Dispatchers.IO) {
        contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
        )
    }

    // ── Delete single message ────────────────────────────────────────────

    suspend fun deleteMessage(messageId: String): Unit = withContext(Dispatchers.IO) {
        contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId),
        )
    }

    // ── Existing methods (updated with threadId + isRead) ────────────────

    suspend fun getInboxMessages(limit: Int = 50): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                return@withContext emptyList()
            }
        }

        if (!hasSmsPermission()) return@withContext emptyList()

        val messages = mutableListOf<SmsMessage>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ,
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )

        cursor?.use {
            val idIndex       = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex  = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex     = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex     = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex     = it.getColumnIndex(Telephony.Sms.TYPE)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val readIndex     = it.getColumnIndex(Telephony.Sms.READ)

            if (idIndex == -1 || addressIndex == -1 || bodyIndex == -1 || dateIndex == -1 || typeIndex == -1 || threadIdIndex == -1) {
                return@withContext emptyList()
            }

            var count = 0
            while (it.moveToNext() && (count < limit)) {
                val id = it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: "Unknown"
                val body = it.getString(bodyIndex) ?: continue
                val timestamp = it.getLong(dateIndex)
                val type = it.getInt(typeIndex)
                val threadId = it.getLong(threadIdIndex)
                val read = it.getInt(readIndex)

                messages.add(
                    SmsMessage(
                        id = id,
                        threadId = threadId,
                        sender = address,
                        text = body,
                        timestamp = timestamp,
                        isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT,
                        isRead = read == 1,
                    ),
                )
                count++
            }
        }

        messages
    }

    suspend fun getMessageById(id: String): SmsMessage? = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) return@withContext null

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ,
            ),
            "${Telephony.Sms._ID} = ?",
            arrayOf(id),
            null
        )

        cursor?.use {
            val typeIdx    = it.getColumnIndex(Telephony.Sms.TYPE)
            val threadIdx  = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val readIdx    = it.getColumnIndex(Telephony.Sms.READ)
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx    = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx    = it.getColumnIndex(Telephony.Sms.DATE)

            if (typeIdx == -1 || threadIdx == -1 || addressIdx == -1 || dateIdx == -1) return@withContext null

            if (it.moveToFirst()) {
                val type     = it.getInt(typeIdx)
                val threadId = it.getLong(threadIdx)
                val read     = if (readIdx != -1) it.getInt(readIdx) else 0
                SmsMessage(
                    id = id,
                    threadId = threadId,
                    sender = it.getString(addressIdx) ?: "Unknown",
                    text = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else "",
                    timestamp = it.getLong(dateIdx),
                    isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT,
                    isRead = read == 1,
                )
            } else null
        }
    }

    /**
     * Sends an SMS to [recipient] and writes it to the sent-messages provider.
     * Handles multi-part messages automatically via [SmsManager.divideMessage].
     * Returns `true` on success, `false` if permission is missing or sending fails.
     */
    suspend fun sendSms(recipient: String, message: String): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return@withContext false
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)

            // Build sent + delivery PendingIntents for each part
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveryIntents = ArrayList<PendingIntent>(parts.size)
            for (i in parts.indices) {
                val sentIntent = PendingIntent.getBroadcast(
                    context, 0,
                    Intent("com.smssentry.SMS_SENT"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val deliveryIntent = PendingIntent.getBroadcast(
                    context, 0,
                    Intent("com.smssentry.SMS_DELIVERED"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                sentIntents.add(sentIntent)
                deliveryIntents.add(deliveryIntent)
            }

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, sentIntents, deliveryIntents)
            } else {
                smsManager.sendTextMessage(recipient, null, message, sentIntents[0], deliveryIntents[0])
            }

            // Write sent message to the content provider
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, message)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            if (com.smssentry.BuildConfig.DEBUG) {
                Log.d(TAG, "SMS sent to $recipient")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    // ── Global Search ───────────────────────────────────────────────────

    /**
     * Full-text search across all SMS messages for the given [query].
     *
     * Uses SQL `LIKE` with escaped wildcards to prevent injection.
     * Returns up to [limit] matching messages sorted by date DESC.
     *
     * Note: This is a linear scan — acceptable for typical inbox sizes (<50K msgs)
     * but would need an FTS index for very large inboxes.
     */
    suspend fun searchMessages(query: String, limit: Int = 50): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission() || query.isBlank()) return@withContext emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )

        // Escape SQL LIKE wildcards so literal '%' and '_' in user input
        // don't match unintended patterns
        val escapedQuery = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        val results = mutableListOf<SmsMessage>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'",
            arrayOf("%$escapedQuery%"),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )

        cursor?.use {
            val idIdx      = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdx  = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addrIdx    = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx    = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx    = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx    = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIdx    = it.getColumnIndex(Telephony.Sms.READ)

            if (idIdx == -1 || addrIdx == -1 || bodyIdx == -1 || dateIdx == -1 || typeIdx == -1) {
                return@withContext emptyList()
            }

            while (it.moveToNext()) {
                val id      = it.getString(idIdx) ?: continue
                val address = it.getString(addrIdx) ?: "Unknown"
                val body    = it.getString(bodyIdx)?.takeIf { b -> b.isNotBlank() } ?: continue

                results.add(
                    SmsMessage(
                        id = id,
                        threadId = if (threadIdx != -1) it.getLong(threadIdx) else 0L,
                        sender = address,
                        text = body,
                        timestamp = it.getLong(dateIdx),
                        isSent = it.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_SENT,
                        isRead = if (readIdx != -1) it.getInt(readIdx) == 1 else false,
                    )
                )
            }
        }
        results
    }
}
