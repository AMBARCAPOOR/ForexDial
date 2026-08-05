package com.reddoor3.forexdial.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.FinnhubClient
import com.reddoor3.forexdial.api.TwelveDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// Self-chaining instead of PeriodicWorkRequest: WorkManager enforces a hard
// 15-minute floor on periodic work (confirmed against Android's own docs
// 2026-07-30), so a genuine 3-minute cadence is only possible by having each
// run re-enqueue the next one. Chained via a UNIQUE one-time work name with
// REPLACE, in a `finally` block so the chain continues regardless of whether
// THIS attempt succeeded or failed.
class ForexSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHAIN_INTERVAL_MINUTES = 3L
        const val CHAIN_WORK_NAME = "forex_sync_chain"

        // CORRECTED 2026-07-30 after a real 8-hour outage: Twelve Data charges
        // credits PER SYMBOL, not per HTTP call (confirmed against their own
        // pricing docs). The original single 6-symbol call every 3 minutes
        // was 6 credits/sync = 2880/day against an 800/day free cap - the key
        // ran out mid-day (2236 used) and every sync failed for the rest of
        // the day, which is what "hasn't refreshed in 8 hours" was.
        //
        // Fix: EUR/USD fetched alone every cycle (1 credit - it's the number
        // that actually needs to move fast). DXY's other 5 pairs are fetched
        // on this slower cadence and cached; DXY is recomputed every cycle
        // from the fresh EUR/USD + the cached basket, so it still updates
        // every 3 min even though the basket itself lags up to this long.
        // Budget: 480 cycles/day x 1 credit (EUR/USD) + 48 basket
        // fetches/day x 5 credits = 480 + 240 = 720/day, under 800 with
        // headroom for manual Sync Now / tap-to-refresh taps.
        const val DXY_BASKET_REFRESH_MINUTES = 30L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            runSync()
        } finally {
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        val next = OneTimeWorkRequestBuilder<ForexSyncWorker>()
            .setInitialDelay(CHAIN_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, next)
    }

    private suspend fun runSync(): Result {
        val prefs = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("sync_started_ts", System.currentTimeMillis()).apply()

        val eurUsd = runCatching { TwelveDataClient.getEurUsdQuote() }.getOrNull()
        val btc    = runCatching { FinnhubClient.getBtc() }.getOrNull()

        // Only spend the 5-credit DXY basket call if it's actually due.
        val basketAgeMs = System.currentTimeMillis() - prefs.getLong("dxy_basket_ts", 0L)
        val basketDue = basketAgeMs >= TimeUnit.MINUTES.toMillis(DXY_BASKET_REFRESH_MINUTES)
        var dxy: Double? = null
        if (eurUsd != null) {
            if (basketDue) {
                val basket = runCatching { TwelveDataClient.getDxyBasket() }.getOrNull()
                if (basket != null) {
                    prefs.edit()
                        .putLong("dxy_basket_ts", System.currentTimeMillis())
                        .putFloat("dxy_jpy", basket.usdJpy.toFloat())
                        .putFloat("dxy_gbp", basket.gbpUsd.toFloat())
                        .putFloat("dxy_cad", basket.usdCad.toFloat())
                        .putFloat("dxy_sek", basket.usdSek.toFloat())
                        .putFloat("dxy_chf", basket.usdChf.toFloat())
                        .apply()
                    dxy = TwelveDataClient.computeDxy(eurUsd.price, basket)
                }
            }
            // Basket not due (or the due fetch failed) - recompute DXY from
            // the fresh EUR/USD plus whatever basket values are cached.
            if (dxy == null && prefs.contains("dxy_jpy")) {
                val cachedBasket = TwelveDataClient.DxyBasket(
                    usdJpy = prefs.getFloat("dxy_jpy", 0f).toDouble(),
                    gbpUsd = prefs.getFloat("dxy_gbp", 0f).toDouble(),
                    usdCad = prefs.getFloat("dxy_cad", 0f).toDouble(),
                    usdSek = prefs.getFloat("dxy_sek", 0f).toDouble(),
                    usdChf = prefs.getFloat("dxy_chf", 0f).toDouble()
                )
                dxy = TwelveDataClient.computeDxy(eurUsd.price, cachedBasket)
            }
        }

        val failures = listOfNotNull(
            if (eurUsd == null) "EUR/USD failed" else null,
            if (dxy == null)    "DXY failed"     else null,
            if (btc == null)    "BTC failed"     else null
        )

        // Never let one failed field blank a previously-good value: preserve
        // the last cached number instead of overwriting with 0f. A missing
        // complication reads as visibly broken; a stale-but-real value is a
        // much smaller problem and self-heals on the next successful cycle.
        // (Found this the hard way 2026-07-30: a lone EUR/USD failure was
        // wiping the watch's rate to blank even though BTC alone succeeding
        // was enough for the old code to still push a "successful" update.)
        val eurUsdOut     = eurUsd?.price?.toFloat()         ?: prefs.getFloat("cached_eurusd", 0f)
        val eurUsdPrevOut = eurUsd?.previousClose?.toFloat() ?: prefs.getFloat("cached_eurusd_prev", 0f)
        val dxyOut        = dxy?.toFloat()                   ?: prefs.getFloat("cached_dxy", 0f)
        val btcOut        = btc?.price?.toFloat()            ?: prefs.getFloat("cached_btc", 0f)

        prefs.edit()
            .putFloat("cached_eurusd",      eurUsdOut)
            .putFloat("cached_eurusd_prev", eurUsdPrevOut)
            .putFloat("cached_dxy",         dxyOut)
            .putFloat("cached_btc",         btcOut)
            .putString("sync_status",       failures.joinToString(", ").ifEmpty { "All OK" })
            .apply()

        if (eurUsd == null && btc == null) return Result.retry()

        // Only evaluate a crossing against a genuinely fresh quote. Using the
        // preserved-cache fallback above would compare the threshold against
        // a value we already compared last cycle, which could re-fire the
        // same crossing (or invent one that never happened) purely because a
        // fetch failed.
        eurUsd?.price?.toFloat()?.let { checkAlertCrossing(prefs, it) }

        val alertTs    = prefs.getLong(Constants.KEY_ALERT_FIRED_TS, 0L)
        val alertDir   = prefs.getString(Constants.KEY_ALERT_FIRED_DIR, "") ?: ""
        val alertLevel = prefs.getFloat(Constants.KEY_ALERT_LEVEL, 0f)

        return try {
            val request = PutDataMapRequest.create(Constants.PATH_FOREX).apply {
                dataMap.putFloat(Constants.WKEY_EURUSD,      eurUsdOut)
                dataMap.putFloat(Constants.WKEY_EURUSD_PREV, eurUsdPrevOut)
                dataMap.putFloat(Constants.WKEY_DXY,         dxyOut)
                dataMap.putFloat(Constants.WKEY_BTC,         btcOut)
                dataMap.putLong(Constants.WKEY_ALERT_TS,     alertTs)
                dataMap.putString(Constants.WKEY_ALERT_DIR,  alertDir)
                dataMap.putFloat(Constants.WKEY_ALERT_LEVEL, alertLevel)
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

    // Fires only on an actual CROSSING of the threshold, not merely on being
    // the wrong side of it - otherwise the alert would re-fire every 3
    // minutes for as long as price stayed past the level, which is exactly
    // the "it's there the whole time" behaviour this is meant to avoid.
    //
    // Records the new price as the baseline every call, so a crossing is
    // detected once, on the cycle it happens, and then not again until price
    // comes back and crosses afresh.
    private fun checkAlertCrossing(
        prefs: android.content.SharedPreferences,
        newPrice: Float
    ) {
        val level = prefs.getFloat(Constants.KEY_ALERT_LEVEL, 0f)
        val prev  = prefs.getFloat(Constants.KEY_ALERT_LAST_PRICE, 0f)

        // Always advance the baseline, even when no threshold is set - so
        // enabling an alert later compares against a current price rather
        // than a stale one from whenever the feature was last used.
        prefs.edit().putFloat(Constants.KEY_ALERT_LAST_PRICE, newPrice).apply()

        if (level <= 0f) return   // alert disabled
        if (prev <= 0f) return    // no baseline yet - first observation only

        val dir = when {
            prev < level && newPrice >= level -> "UP"
            prev > level && newPrice <= level -> "DOWN"
            else -> return
        }

        prefs.edit()
            .putLong(Constants.KEY_ALERT_FIRED_TS, System.currentTimeMillis())
            .putString(Constants.KEY_ALERT_FIRED_DIR, dir)
            .apply()
    }
}
