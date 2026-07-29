package com.reddoor3.forexdial

import android.app.Application
import androidx.work.*
import com.reddoor3.forexdial.work.ForexSyncWorker
import com.reddoor3.forexdial.work.SunriseSunsetWorker
import com.reddoor3.forexdial.work.YieldSpreadWorker
import java.util.concurrent.TimeUnit

class ForexDialApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundWork()
    }

    private fun scheduleBackgroundWork() {
        val wm = WorkManager.getInstance(this)
        val netConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Immediate one-time pushes so watch has data on first launch (periodic work delays ~15 min)
        wm.enqueue(OneTimeWorkRequestBuilder<ForexSyncWorker>().setConstraints(netConstraint).build())
        wm.enqueue(OneTimeWorkRequestBuilder<YieldSpreadWorker>().setConstraints(netConstraint).build())
        wm.enqueue(OneTimeWorkRequestBuilder<SunriseSunsetWorker>().setConstraints(netConstraint).build())

        // Forex prices — every 15 minutes (pushes EUR/USD, DXY, BTC to watch)
        wm.enqueueUniquePeriodicWork(
            "forex_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ForexSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(netConstraint)
                .build()
        )

        // Yield spread — every 15 minutes
        wm.enqueueUniquePeriodicWork(
            "yield_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<YieldSpreadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(netConstraint)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
        )

        // Sunrise/sunset — once daily (lat/lng is fixed so rarely changes)
        wm.enqueueUniquePeriodicWork(
            "sun_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SunriseSunsetWorker>(1, TimeUnit.DAYS)
                .setConstraints(netConstraint)
                .build()
        )
    }
}
