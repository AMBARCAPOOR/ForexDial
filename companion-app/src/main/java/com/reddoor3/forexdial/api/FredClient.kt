package com.reddoor3.forexdial.api

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YieldResult(val spread: Double, val direction: String)

object FredClient {

    // FRED series IDs — long-term government bond yields (10yr proxy for spread)
    private const val SERIES_DE = "IRLTLT01DEM156N"   // Germany
    private const val SERIES_US = "IRLTLT01USM156N"   // United States

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun latestValue(seriesId: String): Double {
        val url = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$seriesId"
        val csv = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("FRED HTTP ${resp.code} for $seriesId")
            resp.body?.string() ?: throw IOException("Empty FRED response")
        }
        // CSV format: header line then DATE,VALUE rows — last non-blank data line wins
        val lastLine = csv.trim().lines()
            .filter { it.isNotBlank() && !it.startsWith("DATE") }
            .lastOrNull() ?: throw IOException("No data rows in FRED CSV")
        return lastLine.split(",")[1].trim().toDoubleOrNull()
            ?: throw IOException("Cannot parse FRED value from: $lastLine")
    }

    // Returns the raw DE-US yield spread and a LONG/SHORT/NEUT direction string.
    // DE > US by >0.10 → LONG EUR/USD conviction; US dominates → SHORT.
    fun getYieldSpread(): YieldResult {
        val de     = latestValue(SERIES_DE)
        val us     = latestValue(SERIES_US)
        val spread = de - us
        val dir    = when {
            spread > 0.10  -> "LONG"
            spread < -0.10 -> "SHORT"
            else           -> "NEUT"
        }
        return YieldResult(spread, dir)
    }
}
