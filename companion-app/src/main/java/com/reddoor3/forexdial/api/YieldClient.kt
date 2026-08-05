package com.reddoor3.forexdial.api

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YieldResult(val spread: Double, val direction: String)

// EUR/USD 2-year yield spread.
//
// Rewritten 2026-08-05 (was FredClient). Two changes, both verified against
// live data before writing this:
//
// 1. TENOR: 10-year -> 2-year. The 2-year tracks near-term central-bank
//    policy expectations, which is the conventional rate differential
//    watched for major FX pairs; the 10-year carries growth/inflation/term
//    premium as well.
//
// 2. FRESHNESS: the old OECD series (IRLTLT01DEM156N / IRLTLT01USM156N) are
//    MONTHLY. On 2026-08-05 they were still serving June 1 values - the
//    watch was showing a two-month-old number to 2dp, which reads far more
//    precise and current than it was. Both legs are now DAILY.
//
// Neither source needs an API key, same as the old FRED CSV endpoint.
//
// EUR leg is the ECB's AAA-rated euro-area government curve rather than
// literal German Bunds. Bundesbank does publish a true DE 2-year
// (BBSIS D.I.ZST.ZI.EUR.S1311.B.A604.R02XX.R.A.A._Z._Z.A, also keyless and
// even slightly fresher), but it differed by only ~0.04 on the day this was
// written - immaterial to a LONG/SHORT/NEUT call - and its CSV is
// semicolon-delimited with German decimal commas ("2,70"), needing a
// separate parser. The AAA euro-area curve is also the standard EUR
// reference, and EUR/USD is a euro-area pair rather than a German one.
object YieldClient {

    private const val FRED_US_2Y = "DGS2"
    private const val ECB_EUR_2Y = "B.U2.EUR.4F.G_N_A.SV_C_YM.SR_2Y"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun fetch(url: String, what: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("$what HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("$what returned an empty body")
        }

    // Walks backwards to the last row that actually parses, rather than
    // trusting the final row. This is defensive, not a fix for an observed
    // failure: "." is FRED's documented missing-value marker, but DGS2 in
    // practice OMITS non-trading days entirely - checked 2026-08-05, zero
    // "." rows across all 13,090 observations. So the plain last-row read
    // the old code used would work today; this just doesn't depend on that
    // staying true, at the cost of a few lines.
    private fun fredLatest(seriesId: String): Double {
        val csv = fetch(
            "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$seriesId",
            "FRED $seriesId"
        )
        return csv.trim().lines().asReversed()
            .firstNotNullOfOrNull { line ->
                if (line.isBlank() || line.startsWith("observation_date") || line.startsWith("DATE")) null
                else line.split(",").getOrNull(1)?.trim()?.toDoubleOrNull()
            }
            ?: throw IOException("No parseable value in FRED $seriesId")
    }

    // ECB csvdata: a header row then one row per observation. Column 9
    // (0-indexed) is OBS_VALUE. Later columns do contain quoted commas
    // (TITLE_COMPL), but every column up to OBS_VALUE is a simple token, so
    // a plain split is safe this far in.
    private fun ecbLatest(seriesKey: String): Double {
        val csv = fetch(
            "https://data-api.ecb.europa.eu/service/data/YC/$seriesKey" +
                "?lastNObservations=1&format=csvdata",
            "ECB $seriesKey"
        )
        val row = csv.trim().lines().firstOrNull { it.startsWith("YC.") }
            ?: throw IOException("No data row in ECB response for $seriesKey")
        return row.split(",").getOrNull(9)?.trim()?.toDoubleOrNull()
            ?: throw IOException("Cannot parse ECB value from: ${row.take(120)}")
    }

    // Raw EUR-US 2-year spread plus a LONG/SHORT/NEUT direction.
    // EUR yields above US by >0.10 -> LONG EUR/USD conviction, and vice versa.
    fun getYieldSpread(): YieldResult {
        val eur    = ecbLatest(ECB_EUR_2Y)
        val us     = fredLatest(FRED_US_2Y)
        val spread = eur - us
        val dir    = when {
            spread >  0.10 -> "LONG"
            spread < -0.10 -> "SHORT"
            else           -> "NEUT"
        }
        return YieldResult(spread, dir)
    }
}
