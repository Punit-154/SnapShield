package com.smssentry.data.repository

import android.content.Context
import android.provider.Telephony
import com.smssentry.data.model.SmsMessage

class SmsRepository(private val context: Context) {

    fun getInboxMessages(limit: Int = 500): List<SmsMessage> {
        return try {
            val messages = mutableListOf<SmsMessage>()
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrCol = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val body = it.getString(bodyCol) ?: continue
                    messages.add(
                        SmsMessage(
                            id = it.getString(idCol),
                            sender = it.getString(addrCol) ?: "Unknown",
                            text = body,
                            timestamp = it.getLong(dateCol)
                        )
                    )
                    count++
                }
            }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getMessageById(id: String): SmsMessage? {
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms._ID} = ?",
                arrayOf(id),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: return null
                    SmsMessage(
                        id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown",
                        text = body,
                        timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}