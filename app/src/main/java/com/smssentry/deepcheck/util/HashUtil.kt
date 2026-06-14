package com.smssentry.deepcheck.util

import java.security.MessageDigest

object HashUtil {
    fun hashSms(sender: String, prefix: String): String {
        val input = "$sender|$prefix"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        // FIX: Use 'toInt() and 0xFF' to handle signed bytes correctly during hex conversion
        return hashBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
