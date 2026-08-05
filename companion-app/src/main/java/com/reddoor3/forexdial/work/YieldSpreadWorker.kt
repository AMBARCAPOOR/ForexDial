package com.reddoor3.forexdial.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.YieldClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// Runs twice a day at 02:00 and 14:00 Pacific (Ambar 2026-08-05), replacing a
// 15-minute poll. Both legs are daily series, so 96 fetches a day were 94
// wasted ones - and re-fetching that often meant the displayed figure could
// change at any moment of the day as ECB and FRED published at their own
// times, which read as the number fluctuating.
//
// 14:00 PT is the one that matters: FRED's DGS2 lands early afternoon ET and
// the ECB curve publishes late morning CET, so by 14:00 PT both legs have
// almost certainly posted for the day. 02:00 PT is a safety net that catches
// anything late without waiting a further 12 hours.
//
// Self-chaining rather than PeriodicWorkRequest, since periodic work fires on
// an elapsed-time cadence and cannot target a wall-clock hour. Same pattern
// as ForexSyncWorker, but the delay here is computed to an absolute clock
// time, so re-anchoring the chain (a manual Sync Now, a reboot) never drifts
// the schedule.
class YieldSpreadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHAIN_WORK_NAME = "yield_sync_chain"

        // Ambar asked for PT specifically, so this is pinned to Pacific
        // rather than following device local time - it stays on schedule if
        // the phone travels.
        private val ZONE = ZoneId.of("America/Los_Angeles")
        private val RUN_HOURS = intArrayOf(2, 14)

        // On a failed fetch, try again well before the next scheduled slot -
        // otherwise one transient network blip leaves the spread stale for up
        // to 12 hours.
        private val RETRY_DELAY_MS = TimeUnit.MINUTES.toMillis(30)

        // Next 02:00 or 14:00 Pacific strictly after nowMs.
        //
        // DST note: on spring-forward day 02:00 Pacific does not exist.
        // atZone() resolves a gap by shifting forward, so that day's early
        // run lands at 03:00 instead of being skipped. On fall-back day
        // 02:00 happens twice and it takes the first. Both are fine - this
        // only needs to be roughly twice daily, not exact.
        fun millisUntilNextRun(nowMs: Long): Long {
            val today = Instant.ofEpochMilli(nowMs).atZone(ZONE).toLocalDate()
            var soonest = Long.MAX_VALUE
            for (dayOffset in 0L..1L) {
                for (hour in RUN_HOURS) {
                    val slot = today.plusDays(dayOffset)
                        .atTime(hour, 0)
                        .atZone(ZONE)
                        .toInstant()
                        .toEpochMilli()
                    if (slot > nowMs && slot - nowMs < soonest) soonest = slot - nowMs
                }
            }
            return soonest
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ok = runCatching { runSync() }.getOrDefault(false)

        // Always re-arm, so the chain can never die and strand the spread.
        scheduleNext(if (ok) millisUntilNextRun(System.currentTimeMillis()) else RETRY_DELAY_MS)

        // Deliberately always success: retry timing is handled by the chain
        // above. Returning retry() here would have WorkManager schedule its
        // own backoff attempt *as well*, double-running the fetch.
        Result.success()
    }

    private fun scheduleNext(delayMs: Long) {
        val next = OneTimeWorkRequestBuilder<YieldSpreadWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, next)
    }

    private suspend fun runSync(): Boolean {
        val result = YieldClient.getYieldSpread()

        val request = PutDataMapRequest.create(Constants.PATH_YIELD).apply {
            dataMap.putString(Constants.WKEY_SENTIMENT,   result.direction)
            dataMap.putFloat(Constants.WKEY_YIELD_SPREAD, result.spread.toFloat())
            dataMap.putFloat(Constants.WKEY_YIELD_PREV,
                result.prevSpread?.toFloat() ?: Float.MAX_VALUE)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(applicationContext).putDataItem(request).await()
        return true
    }
}
