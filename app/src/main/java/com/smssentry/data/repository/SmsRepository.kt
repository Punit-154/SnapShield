package com.smssentry.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.smssentry.data.model.SmsMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    private val contentResolver: ContentResolver
) {

    fun getInboxMessages(limit: Int = 50): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri: Uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")
        val sortOrder = "date DESC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.let {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = it.getString(idIndex) ?: continue
                    val address = it.getString(addressIndex) ?: "Unknown"
                    val body = it.getString(bodyIndex) ?: continue
                    val timestamp = it.getLong(dateIndex)

                    messages.add(
                        SmsMessage(
                            id = id,
                            sender = address,
                            text = body,
                            timestamp = timestamp
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return messages
    }

    fun getMessageById(id: String): SmsMessage? {
        val uri: Uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")
        val selection = "_id = ?"
        val selectionArgs = arrayOf(id)

        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                if (it.moveToFirst()) {
                    val addressIndex = it.getColumnIndex("address")
                    val bodyIndex = it.getColumnIndex("body")
                    val dateIndex = it.getColumnIndex("date")

                    SmsMessage(
                        id = id,
                        sender = it.getString(addressIndex) ?: "Unknown",
                        text = it.getString(bodyIndex) ?: "",
                        timestamp = it.getLong(dateIndex)
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            cursor?.close()
        }
    }
}
