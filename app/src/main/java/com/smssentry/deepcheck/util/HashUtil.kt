package com.smssentry.deepcheck.util

import java.security.MessageDigest

object HashUtil {
    fun hashSms(sender: String, prefix: String): String {
        val input = "$sender|$prefix"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
