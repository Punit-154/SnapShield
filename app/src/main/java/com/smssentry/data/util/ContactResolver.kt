package com.smssentry.data.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves phone numbers to contact display names and photo URIs.
 * Uses an LRU cache to avoid repeated ContentResolver queries.
 */
@Singleton
class ContactResolver @Inject constructor(
    private val context: Context
) {
    data class ContactInfo(
        val displayName: String,
        val photoUri: String? = null,
        val contactId: Long? = null
    )

    // Cache up to 200 resolved contacts
    private val cache = LruCache<String, ContactInfo>(200)

    /**
     * Resolve a phone number to a contact name and photo.
     * Returns ContactInfo with the original address as displayName if no contact found.
     */
    @Synchronized
    fun resolve(address: String): ContactInfo {
        if (address.isBlank()) return ContactInfo(displayName = "Unknown")

        val normalized = normalizeNumber(address)
        cache.get(normalized)?.let { return it }

        val info = lookupContact(address) ?: ContactInfo(displayName = formatNumber(address))
        cache.put(normalized, info)
        return info
    }

    /**
     * Get just the display name for a phone number.
     */
    fun getDisplayName(address: String): String = resolve(address).displayName

    private fun lookupContact(address: String): ContactInfo? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup._ID
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContactInfo(
                        displayName = cursor.getString(0) ?: formatNumber(address),
                        photoUri = cursor.getString(1),
                        contactId = cursor.getLong(2)
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeNumber(number: String): String {
        return PhoneNumberUtils.normalizeNumber(number) ?: number
    }

    private fun formatNumber(number: String): String {
        return PhoneNumberUtils.formatNumber(number, java.util.Locale.getDefault().country)
            ?: number
    }

    fun clearCache() {
        cache.evictAll()
    }
}
