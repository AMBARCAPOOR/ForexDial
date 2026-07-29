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
        wm.enqueueUniquePeriodicWork(
            "forex_sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ForexSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net).build()
        )
        wm.enqueueUniquePeriodicWork(
            "yield_sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<YieldSpreadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net).build()
        )
        wm.enqueueUniquePeriodicWork(
            "sun_sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SunriseSunsetWorker>(1, TimeUnit.DAYS)
                .setConstraints(net).build()
        )
    }
}
