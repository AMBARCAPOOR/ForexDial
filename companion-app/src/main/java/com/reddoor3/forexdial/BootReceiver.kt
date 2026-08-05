package com.reddoor3.forexdial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.reddoor3.forexdial.work.ForexSyncWorker
import com.reddoor3.forexdial.work.SunriseSunsetWorker
import com.reddoor3.forexdial.work.YieldSpreadWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val wm = WorkManager.getInstance(context)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        // Not periodic - see ForexDialApplication.kt / ForexSyncWorker.kt.
        // A boot-time reboot kills any pending self-chained request, so this
        // one-time enqueue is what restarts the 3-minute chain after reboot.
        wm.enqueue(OneTimeWorkRequestBuilder<ForexSyncWorker>().setConstraints(net).build())
        // Yield is self-chaining to 02:00/14:00 Pacific as of 2026-08-05, and
        // a reboot kills the pending chained request - so this one-time
        // enqueue restarts it. It runs once now and then re-arms to the next
        // clock slot, same as the forex chain above.
        wm.cancelUniqueWork("yield_sync")   // clear the old 15-min periodic
        wm.enqueue(OneTimeWorkRequestBuilder<YieldSpreadWorker>().setConstraints(net).build())
        wm.enqueueUniquePeriodicWork(
            "sun_sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SunriseSunsetWorker>(1, TimeUnit.DAYS)
                .setConstraints(net).build()
        )
    }
}
