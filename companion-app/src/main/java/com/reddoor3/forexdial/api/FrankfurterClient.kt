package com.reddoor3.forexdial.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.pow

object FrankfurterClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Returns EUR/USD rate and approximate DXY
    // DXY = 50.14348112 × EURUSD^-0.576 × USDJPY^0.136 × GBPUSD^-0.119
    //                    × USDCAD^0.091 × USDSEK^0.042 × USDCHF^0.036
    data class ForexRates(val eurUsd: Double, val dxy: Double)

    fun getRates(): ForexRates =
        fetchRates("https://api.frankfurter.app/latest?from=EUR&to=USD,JPY,GBP,CAD,SEK,CHF")

    // Fetch historical rates for a specific date (YYYY-MM-DD).
    // Frankfurter returns the nearest prior business day if requested date has no data.
    fun getRatesForDate(date: String): ForexRates =
        fetchRates("https://api.frankfurter.app/$date?from=EUR&to=USD,JPY,GBP,CAD,SEK,CHF")

    private fun fetchRates(url: String): ForexRates {
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Frankfurter HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty response")
        }
        val rates = JSONObject(body).getJSONObject("rates")

        val eurUsd = rates.getDouble("USD")
        val eurJpy = rates.getDouble("JPY")
        val eurGbp = rates.getDouble("GBP")
        val eurCad = rates.getDouble("CAD")
        val eurSek = rates.getDouble("SEK")
        val eurChf = rates.getDouble("CHF")

        val usdJpy = eurJpy / eurUsd
        val gbpUsd = eurUsd / eurGbp
        val usdCad = eurCad / eurUsd
        val usdSek = eurSek / eurUsd
        val usdChf = eurChf / eurUsd

        val dxy = 50.14348112 *
            eurUsd.pow(-0.576) *
            usdJpy.pow(0.136) *
            gbpUsd.pow(-0.119) *
            usdCad.pow(0.091) *
            usdSek.pow(0.042) *
            usdChf.pow(0.036)

        return ForexRates(eurUsd = eurUsd, dxy = dxy)
    }
}
