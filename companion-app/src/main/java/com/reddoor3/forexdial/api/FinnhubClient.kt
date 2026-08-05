package com.reddoor3.forexdial.api

import com.reddoor3.forexdial.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ForexQuote(
    val price: Double,
    val prevClose: Double,
    val change: Double,
    val changePct: Double
) {
    val isUp: Boolean get() = change >= 0.0
}

object FinnhubClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun quote(symbol: String): ForexQuote {
        val token = ApiKeys.require(ApiKeys.finnhub, "Finnhub")
        val url = "https://finnhub.io/api/v1/quote?symbol=${symbol}&token=$token"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Finnhub HTTP ${resp.code} for $symbol")
            resp.body?.string() ?: throw IOException("Empty response for $symbol")
        }
        val j = JSONObject(body)
        return ForexQuote(
            price      = j.getDouble("c"),
            prevClose  = j.getDouble("pc"),
            change     = j.getDouble("d"),
            changePct  = j.getDouble("dp")
        )
    }

    fun getEurUsd(): ForexQuote = quote(Constants.SYMBOL_EURUSD)
    fun getDxy(): ForexQuote    = quote(Constants.SYMBOL_DXY)
    fun getBtc(): ForexQuote    = quote(Constants.SYMBOL_BTC)
}
