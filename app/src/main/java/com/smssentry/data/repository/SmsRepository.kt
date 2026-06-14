package com.smssentry.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.smssentry.data.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val context: Context,
) {

    suspend fun getInboxMessages(limit: Int = 50): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                return@withContext emptyList()
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        val messages = mutableListOf<SmsMessage>()
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.TYPE} = ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            "${Telephony.Sms.DATE} DESC",
        )

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            var count = 0
            while (it.moveToNext() && (count < limit)) {
                val id = it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: "Unknown"
                val body = it.getString(bodyIndex) ?: continue
                val timestamp = it.getLong(dateIndex)

                messages.add(
                    SmsMessage(
                        id = id,
                        sender = address,
                        text = body,
                        timestamp = timestamp,
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
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms._ID} = ?",
            arrayOf(id),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                SmsMessage(
                    id = id,
                    sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown",
                    text = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                    timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                )
            } else null
        }
    }
}
