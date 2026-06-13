package com.smssentry.deepcheck.util

import org.junit.Assert.*
import org.junit.Test

class DomainMatchUtilTest {

    @Test
    fun `extractEtldPlus1 for simple domain`() {
        assertEquals("example.com", DomainMatchUtil.extractEtldPlus1("example.com"))
    }

    @Test
    fun `extractEtldPlus1 for subdomain`() {
        assertEquals("example.com", DomainMatchUtil.extractEtldPlus1("sub.example.com"))
    }

    @Test
    fun `extractEtldPlus1 for co in`() {
        assertEquals("hsbc.co.in", DomainMatchUtil.extractEtldPlus1("hsbc.co.in"))
    }

    @Test
    fun `extractEtldPlus1 for deep subdomain with multi-part TLD`() {
        assertEquals("verify-account.xyz", DomainMatchUtil.extractEtldPlus1("hsbc.co.in.verify-account.xyz"))
    }

    @Test
    fun `domainMatchesOfficial for exact match`() {
        assertTrue(DomainMatchUtil.domainMatchesOfficial("hsbc.co.in", "hsbc.co.in"))
    }

    @Test
    fun `domainMatchesOfficial for subdomain match`() {
        assertTrue(DomainMatchUtil.domainMatchesOfficial("www.hsbc.co.in", "hsbc.co.in"))
    }

    @Test
    fun `domainMatchesOfficial for mismatch`() {
        assertFalse(DomainMatchUtil.domainMatchesOfficial("hsbc-secure.xyz", "hsbc.co.in"))
    }

    @Test
    fun `domainMatchesOfficial for subdomain spoofing`() {
        assertFalse(DomainMatchUtil.domainMatchesOfficial("hsbc.co.in.verify-account.xyz", "hsbc.co.in"))
    }

    @Test
    fun `extractEtldPlus1 for com au`() {
        assertEquals("example.com.au", DomainMatchUtil.extractEtldPlus1("www.example.com.au"))
    }
}
