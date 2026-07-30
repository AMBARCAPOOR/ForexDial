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
//
// CORRECTED 2026-07-30 (real outage, not a hypothetical): credits are
// charged PER SYMBOL, not per HTTP call - confirmed directly against Twelve
// Data's own pricing docs after the key blew its 800/day quota by 2236
// credits in under a day. The original getSnapshot() batched 6 symbols every
// 3-minute cycle = 6 credits/sync = 2880 credits/day, 3.6x over budget. The
// batching-is-free assumption (from 2026-07-29) was only verified for
// "does one HTTP call return all 6 symbols" (yes), never for "does it cost
// 1 credit or 6" - that gap caused an 8-hour outage.
//
// Fix: EUR/USD is fetched ALONE every cycle (1 credit - this is the number
// that actually needs to move fast). DXY's other 5 pairs are fetched far
// less often (getDxyBasket, 5 credits) and cached; DXY is recomputed every
// cycle using the FRESH EUR/USD paired with the cached basket, so it still
// updates every 3 minutes even though the basket itself only refreshes
// periodically. See ForexSyncWorker for the caching/cadence logic.
object TwelveDataClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class EurUsdQuote(val price: Double, val previousClose: Double)
    data class DxyBasket(
        val usdJpy: Double, val gbpUsd: Double,
        val usdCad: Double, val usdSek: Double, val usdChf: Double
    )

    // 1 credit.
    fun getEurUsdQuote(): EurUsdQuote {
        val url = "https://api.twelvedata.com/quote?symbol=EUR/USD&apikey=${BuildConfig.TWELVE_DATA_API_KEY}"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Twelve Data HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty response")
        }
        val root = JSONObject(body)
        return EurUsdQuote(root.getDouble("close"), root.getDouble("previous_close"))
    }

    // 5 credits - the other pairs DXY's approximation formula needs. EUR/USD
    // is deliberately excluded here; the fast poll already has a fresher one.
    fun getDxyBasket(): DxyBasket {
        val symbols = "USD/JPY,GBP/USD,USD/CAD,USD/SEK,USD/CHF"
        val url = "https://api.twelvedata.com/quote?symbol=$symbols&apikey=${BuildConfig.TWELVE_DATA_API_KEY}"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Twelve Data HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty response")
        }
        val root = JSONObject(body)
        fun closeOf(symbol: String): Double = root.getJSONObject(symbol).getDouble("close")
        return DxyBasket(
            usdJpy = closeOf("USD/JPY"),
            gbpUsd = closeOf("GBP/USD"),
            usdCad = closeOf("USD/CAD"),
            usdSek = closeOf("USD/SEK"),
            usdChf = closeOf("USD/CHF")
        )
    }

    // Same DXY approximation formula as before - just fed a fresh eurUsd and
    // a (possibly cached, up to ~30 min old) basket instead of always-fresh.
    fun computeDxy(eurUsd: Double, basket: DxyBasket): Double =
        50.14348112 *
            eurUsd.pow(-0.576) *
            basket.usdJpy.pow(0.136) *
            basket.gbpUsd.pow(-0.119) *
            basket.usdCad.pow(0.091) *
            basket.usdSek.pow(0.042) *
            basket.usdChf.pow(0.036)
}
