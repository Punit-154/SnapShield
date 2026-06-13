package com.smssentry.deepcheck.util

import org.junit.Assert.*
import org.junit.Test

class HashUtilTest {

    @Test
    fun `hashSms produces consistent output`() {
        val hash1 = HashUtil.hashSms("+1234567890", "Hello Wor")
        val hash2 = HashUtil.hashSms("+1234567890", "Hello Wor")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashSms produces different output for different input`() {
        val hash1 = HashUtil.hashSms("+1234567890", "Hello Wor")
        val hash2 = HashUtil.hashSms("+0987654321", "Hello Wor")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashSms returns 64 char hex string`() {
        val hash = HashUtil.hashSms("test", "data")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
