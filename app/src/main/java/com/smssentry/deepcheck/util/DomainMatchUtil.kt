package com.smssentry.deepcheck.util

object DomainMatchUtil {

    private val MULTI_PART_TLDS = setOf(
        "co.in", "com.au", "org.uk", "co.uk", "com.br", "co.jp",
        "co.kr", "com.cn", "com.mx", "co.nz", "com.sg", "com.hk",
        "com.tw", "co.za", "com.ar", "co.in", "org.in", "net.in",
        "gov.in", "edu.in"
    )

    fun extractEtldPlus1(domain: String): String {
        val lower = domain.lowercase().trim()
        val parts = lower.split(".")

        if (parts.size <= 2) return lower

        val lastTwo = parts.takeLast(2).joinToString(".")
        if (lastTwo in MULTI_PART_TLDS && parts.size >= 3) {
            return parts.takeLast(3).joinToString(".")
        }

        return parts.takeLast(2).joinToString(".")
    }

    fun domainMatchesOfficial(linkedDomain: String, officialDomain: String): Boolean {
        val linkedEtld1 = extractEtldPlus1(linkedDomain)
        val officialEtld1 = extractEtldPlus1(officialDomain)
        return linkedEtld1 == officialEtld1
    }
}
