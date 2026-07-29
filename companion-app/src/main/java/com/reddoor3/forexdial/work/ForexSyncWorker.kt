package com.reddoor3.forexdial.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.FinnhubClient
import com.reddoor3.forexdial.api.FrankfurterClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ForexSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("sync_started_ts", System.currentTimeMillis()).apply()

        val forex = runCatching { FrankfurterClient.getRates() }.getOrNull()
        val btc   = runCatching { FinnhubClient.getBtc() }.getOrNull()

        val failures = listOfNotNull(
            if (forex == null) "EUR/USD+DXY failed" else null,
            if (btc   == null) "BTC failed" else null
        )

        prefs.edit()
            .putFloat("cached_eurusd", forex?.eurUsd?.toFloat() ?: 0f)
            .putFloat("cached_dxy",    forex?.dxy?.toFloat()    ?: 0f)
            .putFloat("cached_btc",    btc?.price?.toFloat()    ?: 0f)
            .putString("sync_status",  failures.joinToString(", ").ifEmpty { "All OK" })
            .apply()

        if (forex == null && btc == null) return@withContext Result.retry()

        // Daily baseline: previous day's EUR/USD close for intraday pips calculation.
        // Fetched once per calendar day; Frankfurter returns nearest prior business day.
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val prevEurUsd: Float = if (prefs.getString("baseline_date", null) != today) {
            // Walk back day by day to the most recent session that actually has a
            // rate. minusDays(1) alone lands on Sunday every Monday, and on
            // holidays, where ECB publishes nothing.
            var base = 0f
            var probe = LocalDate.now().minusDays(1)
            repeat(5) {
                if (base == 0f) {
                    base = runCatching {
                        FrankfurterClient.getRatesForDate(
                            probe.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        ).eurUsd.toFloat()
                    }.getOrNull() ?: 0f
                    if (base == 0f) probe = probe.minusDays(1)
                }
            }
            // NEVER fall back to today's live price. That makes baseline == price,
            // which renders as a confident "+0 pips / +0.00%" - indistinguishable
            // from a genuinely flat market. A fabricated value is worse than a
            // missing one. 0f means "unknown"; the watch hides the pips bar when
            // prev <= 0, so the failure is visible instead of silent.
            if (base != 0f) {
                prefs.edit().putString("baseline_date", today).putFloat("baseline_eurusd", base).apply()
            }
            base
        } else {
            // Same rule: no baseline means 0f, not today's price.
            prefs.getFloat("baseline_eurusd", 0f)
        }

        return@withContext try {
            val request = PutDataMapRequest.create(Constants.PATH_FOREX).apply {
                dataMap.putFloat(Constants.WKEY_EURUSD,      forex?.eurUsd?.toFloat() ?: 0f)
                dataMap.putFloat(Constants.WKEY_EURUSD_PREV, prevEurUsd)
                dataMap.putFloat(Constants.WKEY_DXY,         forex?.dxy?.toFloat()    ?: 0f)
                dataMap.putFloat(Constants.WKEY_BTC,         btc?.price?.toFloat()    ?: 0f)
                dataMap.putLong("ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(applicationContext).putDataItem(request).await()
            prefs.edit()
                .putLong("last_sync_ts", System.currentTimeMillis())
                .putString("sync_status", failures.joinToString(", ").ifEmpty { "All OK" } + " | sent to watch")
                .apply()
            Result.success()
        } catch (e: Exception) {
            prefs.edit().putString("sync_status", "Watch push failed: ${e.message}").apply()
            Result.retry()
        }
    }
}
