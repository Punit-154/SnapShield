package com.smssentry.deepcheck.tools

import com.smssentry.deepcheck.data.OfficialSitesRepository
import com.smssentry.deepcheck.prefilter.FastPathFilter
import com.smssentry.deepcheck.util.DomainMatchUtil

object BrandMismatchHeuristic {

    fun check(smsText: String, urls: List<String>, officialSites: OfficialSitesRepository): String? {
        val brand = officialSites.findMatchingBrand(smsText) ?: return null

        if (urls.isEmpty()) return null

        val officialDomain = officialSites.lookupOfficialDomain(brand) ?: return null
        val linkDomains = FastPathFilter.extractDomains(urls)

        for (linkDomain in linkDomains) {
            if (!DomainMatchUtil.domainMatchesOfficial(linkDomain, officialDomain)) {
                return "SMS claims to be from '$brand' but link points to '$linkDomain' (official: $officialDomain)"
            }
        }

        return null
    }
}
