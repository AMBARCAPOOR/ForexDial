package com.reddoor3.forexdial

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.reddoor3.forexdial.work.ForexSyncWorker
import com.reddoor3.forexdial.work.YieldSpreadWorker

// Receives the watch's tap-to-refresh request (RefreshRequestReceiver on the
// watch side) and runs the same expedited sync as the phone's own Sync Now
// button (see MainActivity.triggerSync - measured ~2.7s end to end
// 2026-07-30).
class RefreshRequestListenerService : WearableListenerService() {

    companion object {
        private const val REQUEST_PATH = "/forexdial/request_sync"
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != REQUEST_PATH) return
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(applicationContext).apply {
            enqueue(OneTimeWorkRequestBuilder<ForexSyncWorker>()
                .setConstraints(net)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build())
            enqueue(OneTimeWorkRequestBuilder<YieldSpreadWorker>()
                .setConstraints(net)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build())
        }
    }
}
