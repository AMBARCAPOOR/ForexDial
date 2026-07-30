package com.reddoor3.forexdial.api

import com.reddoor3.forexdial.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.pow

// Replaces Frankfurter for the "live" EUR/USD price: Frankfurter is an ECB
// reference rate published ONCE PER DAY (confirmed 2026-07-29 by querying it
// twice seconds apart and getting identical values), so it can never drive
// intraday direction colouring or a pips bar no matter how it's fetched.
// Twelve Data's free tier is genuinely intraday (confirmed live: price moved
// between two calls 8 seconds apart) and its quote endpoint returns
// previous_close pre-computed, so the app no longer needs to walk back
// calendar days looking for a baseline.
object TwelveDataClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class EurUsdQuote(val price: Double, val previousClose: Double)
    data class ForexSnapshot(val eurUsd: EurUsdQuote, val dxy: Double)

    // One batched call for EUR/USD + DXY's full basket. Confirmed working
    // 2026-07-29: all 6 symbols return in a single response keyed by symbol,
    // so DXY costs zero extra API calls beyond the EUR/USD quote itself.
    fun getSnapshot(): ForexSnapshot {
        val symbols = "EUR/USD,USD/JPY,GBP/USD,USD/CAD,USD/SEK,USD/CHF"
        val url = "https://api.twelvedata.com/quote?symbol=$symbols&apikey=${BuildConfig.TWELVE_DATA_API_KEY}"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Twelve Data HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty response")
        }
        val root = JSONObject(body)

        fun closeOf(symbol: String): Double = root.getJSONObject(symbol).getDouble("close")

        val eurUsdObj = root.getJSONObject("EUR/USD")
        val eurUsd = eurUsdObj.getDouble("close")
        val eurUsdPrev = eurUsdObj.getDouble("previous_close")

        val usdJpy = closeOf("USD/JPY")
        val gbpUsd = closeOf("GBP/USD")
        val usdCad = closeOf("USD/CAD")
        val usdSek = closeOf("USD/SEK")
        val usdChf = closeOf("USD/CHF")

        // Same DXY approximation formula as the old FrankfurterClient.
        val dxy = 50.14348112 *
            eurUsd.pow(-0.576) *
            usdJpy.pow(0.136) *
            gbpUsd.pow(-0.119) *
            usdCad.pow(0.091) *
            usdSek.pow(0.042) *
            usdChf.pow(0.036)

        return ForexSnapshot(EurUsdQuote(eurUsd, eurUsdPrev), dxy)
    }
}
