package com.reddoor3.forexdial.api

import com.reddoor3.forexdial.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

data class SunData(
    val sunriseEpoch: Long,
    val sunsetEpoch: Long
) {
    /** 0.0 = sunrise, 1.0 = sunset, clamped to [0, 1]. Use this to position the sun arc. */
    fun arcPercent(nowEpoch: Long = System.currentTimeMillis() / 1000): Float {
        val totalDaylight = (sunsetEpoch - sunriseEpoch).coerceAtLeast(1)
        val elapsed = nowEpoch - sunriseEpoch
        return (elapsed.toFloat() / totalDaylight.toFloat()).coerceIn(0f, 1f)
    }
}

object SunriseSunsetClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getSunData(): SunData {
        val url = "https://api.sunrise-sunset.org/json" +
                "?lat=${Constants.SUN_LAT}&lng=${Constants.SUN_LNG}&formatted=0"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Sunrise-sunset HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty sunrise-sunset response")
        }
        val results = JSONObject(body).getJSONObject("results")
        val sunrise = ZonedDateTime.parse(results.getString("sunrise")).toEpochSecond()
        val sunset  = ZonedDateTime.parse(results.getString("sunset")).toEpochSecond()
        return SunData(sunrise, sunset)
    }
}
