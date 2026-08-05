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
        // ForexSyncWorker is NOT periodic (see below) - this single enqueue is
        // what kicks off its self-chaining 3-minute loop.
        wm.enqueue(OneTimeWorkRequestBuilder<ForexSyncWorker>().setConstraints(netConstraint).build())
        wm.enqueue(OneTimeWorkRequestBuilder<YieldSpreadWorker>().setConstraints(netConstraint).build())
        wm.enqueue(OneTimeWorkRequestBuilder<SunriseSunsetWorker>().setConstraints(netConstraint).build())

        // Forex prices: NOT scheduled here as PeriodicWorkRequest - WorkManager
        // enforces a hard 15-minute floor on periodic work, too slow for the
        // 3-minute cadence Ambar wants (2026-07-30). ForexSyncWorker instead
        // re-enqueues itself every 3 minutes from inside doWork() (see
        // ForexSyncWorker.scheduleNext()); the one-time enqueue above starts
        // that chain. 3 min = 480 Twelve Data requests/day, under the 800/day
        // free cap.

        // Yield spread: NOT periodic either, as of 2026-08-05. It now runs at
        // 02:00 and 14:00 Pacific, and PeriodicWorkRequest fires on elapsed
        // time rather than a wall-clock hour. YieldSpreadWorker re-enqueues
        // itself to the next slot (see YieldSpreadWorker.scheduleNext); the
        // one-time enqueue above starts that chain.
        //
        // Cancel the old periodic registration explicitly - existing installs
        // still have "yield_sync" enqueued from a previous version, and
        // nothing else would ever clear it, so it would keep polling every 15
        // minutes forever alongside the new chain.
        wm.cancelUniqueWork("yield_sync")

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
