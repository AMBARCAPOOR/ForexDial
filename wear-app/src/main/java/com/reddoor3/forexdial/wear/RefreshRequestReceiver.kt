package com.reddoor3.forexdial.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.Wearable
import com.reddoor3.forexdial.wear.complication.AlertComplicationService
import com.reddoor3.forexdial.wear.complication.EurUsdComplicationService
import com.reddoor3.forexdial.wear.complication.EurUsdPipComplicationService
import com.reddoor3.forexdial.wear.complication.EurUsdPipsComplicationService
import com.reddoor3.forexdial.wear.complication.TopRowComplicationService

// Fired by tapping the EUR/USD or top-row complication. Asks the phone for a
// fresh sync (via MessageClient - separate from the DataItem push that's
// unreliable on this device), then forces every forex complication to
// re-query after giving the phone time to respond. Works even if the push's
// own "wake up" notification fails, since refreshFromDataLayer (called
// unconditionally on every complication request as of 2026-07-29) pulls
// directly from the Data Layer's storage rather than depending on that
// notification arriving.
class RefreshRequestReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH = "com.reddoor3.forexdial.wear.ACTION_REFRESH"
        const val REQUEST_PATH = "/forexdial/request_sync"

        // Measured phone-side sync time is ~2.7s (2026-07-30); this delay
        // covers that plus round-trip margin before forcing a re-query.
        private const val REFRESH_DELAY_MS = 4000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
        val pending = goAsync()

        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, REQUEST_PATH, ByteArray(0))
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            listOf(
                EurUsdComplicationService::class.java,
                EurUsdPipComplicationService::class.java,
                EurUsdPipsComplicationService::class.java,
                TopRowComplicationService::class.java,
                // Included 2026-08-05: without this, a fired alert could take
                // up to its full poll period to appear, since the phone's
                // push notification is unreliable on this device. A tap
                // anywhere on the face now surfaces a pending alert too.
                AlertComplicationService::class.java
            ).forEach { cls ->
                runCatching {
                    ComplicationDataSourceUpdateRequester
                        .create(context, ComponentName(context, cls))
                        .requestUpdateAll()
                }
            }
            pending.finish()
        }, REFRESH_DELAY_MS)
    }
}
