package com.reddoor3.forexdial.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.FinnhubClient
import com.reddoor3.forexdial.api.TwelveDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ForexSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("sync_started_ts", System.currentTimeMillis()).apply()

        // Twelve Data, not Frankfurter: Frankfurter is an ECB reference rate
        // published once per business day (confirmed 2026-07-29), so it can
        // never drive intraday direction colouring no matter how it's synced.
        // One batched call returns EUR/USD's live price AND previous_close
        // together, plus DXY's whole 6-pair basket - no more day-walking
        // logic needed to find a baseline.
        val forex = runCatching { TwelveDataClient.getSnapshot() }.getOrNull()
        val btc   = runCatching { FinnhubClient.getBtc() }.getOrNull()

        val failures = listOfNotNull(
            if (forex == null) "EUR/USD+DXY failed" else null,
            if (btc   == null) "BTC failed" else null
        )

        prefs.edit()
            .putFloat("cached_eurusd", forex?.eurUsd?.price?.toFloat() ?: 0f)
            .putFloat("cached_dxy",    forex?.dxy?.toFloat()           ?: 0f)
            .putFloat("cached_btc",    btc?.price?.toFloat()           ?: 0f)
            .putString("sync_status",  failures.joinToString(", ").ifEmpty { "All OK" })
            .apply()

        if (forex == null && btc == null) return@withContext Result.retry()

        return@withContext try {
            val request = PutDataMapRequest.create(Constants.PATH_FOREX).apply {
                dataMap.putFloat(Constants.WKEY_EURUSD,      forex?.eurUsd?.price?.toFloat() ?: 0f)
                // NEVER fall back to the live price for the baseline - that makes
                // prev == price, which renders as a confident "+0 pips / +0.00%",
                // indistinguishable from a genuinely flat market. 0f means
                // "unknown"; the watch hides the pips bar when prev <= 0.
                dataMap.putFloat(Constants.WKEY_EURUSD_PREV, forex?.eurUsd?.previousClose?.toFloat() ?: 0f)
                dataMap.putFloat(Constants.WKEY_DXY,         forex?.dxy?.toFloat() ?: 0f)
                dataMap.putFloat(Constants.WKEY_BTC,         btc?.price?.toFloat() ?: 0f)
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
