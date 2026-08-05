package com.reddoor3.forexdial.api

import com.reddoor3.forexdial.BuildConfig

// Thrown instead of firing a request that is guaranteed to fail auth. The
// message is user-facing - it surfaces on the phone's status line.
class MissingApiKeyException(val service: String) : Exception(
    "No $service API key. Add it to local.properties and rebuild."
)

// Both keys come from local.properties via BuildConfig (see build.gradle.kts)
// and default to "" when it's absent - so a fresh clone builds with no key
// baked in and whoever builds it must supply their own.
object ApiKeys {

    val twelveData: String get() = BuildConfig.TWELVE_DATA_API_KEY
    val finnhub: String    get() = BuildConfig.FINNHUB_API_KEY

    fun require(key: String, service: String): String {
        if (key.isBlank()) throw MissingApiKeyException(service)
        return key
    }

    // Names of the services with no key configured. Empty = fully set up.
    // The yield spread is deliberately absent: ECB and FRED need no key, so
    // that part of the face keeps working even with nothing configured.
    fun missing(): List<String> = buildList {
        if (twelveData.isBlank()) add("Twelve Data")
        if (finnhub.isBlank()) add("Finnhub")
    }

    fun setupHint(): String {
        val gaps = missing()
        if (gaps.isEmpty()) return ""
        return buildString {
            append("⚠ Missing API key(s): ${gaps.joinToString(", ")}.\n")
            append("Add to local.properties and rebuild:\n")
            if (twelveData.isBlank()) append("  twelveDataApiKey=…   (free: twelvedata.com)\n")
            if (finnhub.isBlank())    append("  finnhubApiKey=…      (free: finnhub.io)\n")
            append("Yield still works without keys (ECB/FRED need none).")
        }
    }
}
