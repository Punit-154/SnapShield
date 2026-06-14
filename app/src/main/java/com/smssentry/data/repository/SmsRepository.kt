package com.smssentry.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
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

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
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
            val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIdx  = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx     = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx     = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx     = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx     = it.getColumnIndexOrThrow(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val threadId = it.getLong(threadIdIdx)
                val address  = it.getString(addressIdx) ?: "Unknown"
                val body     = it.getString(bodyIdx) ?: ""
                val date     = it.getLong(dateIdx)
                val type     = it.getInt(typeIdx)
                val read     = it.getInt(readIdx)

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
            val idIdx       = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIdx  = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx     = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx     = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx     = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx     = it.getColumnIndexOrThrow(Telephony.Sms.READ)
            val threadIdx   = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)

            while (it.moveToNext()) {
                val id      = it.getString(idIdx) ?: continue
                val address = it.getString(addressIdx) ?: "Unknown"
                val body    = it.getString(bodyIdx)?.takeIf { b -> b.isNotBlank() } ?: continue
                val date    = it.getLong(dateIdx)
                val type    = it.getInt(typeIdx)
                val read    = it.getInt(readIdx)
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
            val idIndex       = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex  = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex     = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex     = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex     = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val threadIdIndex = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val readIndex     = it.getColumnIndexOrThrow(Telephony.Sms.READ)

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
            if (it.moveToFirst()) {
                val type     = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val threadId = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                val read     = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ))
                SmsMessage(
                    id = id,
                    threadId = threadId,
                    sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown",
                    text = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                    timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                    isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT,
                    isRead = read == 1,
                )
            } else null
        }
    }

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
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipient, null, message, null, null)
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
     * Search all SMS messages across all conversations for the given query string.
     * Returns up to [limit] matching messages with their thread and sender info.
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

        val results = mutableListOf<SmsMessage>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.BODY} LIKE ?",
            arrayOf("%$query%"),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )

        cursor?.use {
            val idIdx      = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIdx  = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addrIdx    = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx    = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx    = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx    = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx    = it.getColumnIndexOrThrow(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val id      = it.getString(idIdx) ?: continue
                val address = it.getString(addrIdx) ?: "Unknown"
                val body    = it.getString(bodyIdx)?.takeIf { b -> b.isNotBlank() } ?: continue

                results.add(
                    SmsMessage(
                        id = id,
                        threadId = it.getLong(threadIdx),
                        sender = address,
                        text = body,
                        timestamp = it.getLong(dateIdx),
                        isSent = it.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_SENT,
                        isRead = it.getInt(readIdx) == 1,
                    )
                )
            }
        }
        results
    }
}
