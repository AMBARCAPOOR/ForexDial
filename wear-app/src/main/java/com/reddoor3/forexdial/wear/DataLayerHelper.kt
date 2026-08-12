package com.reddoor3.forexdial.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object DataLayerHelper {

    private const val TAG = "FDD_DataLayerHelper"

    suspend fun refreshFromDataLayer(context: Context) {
        Log.d(TAG, "refreshFromDataLayer: ENTER")
        try {
            val buffer = Wearable.getDataClient(context).getDataItems().await()
            Log.d(TAG, "refreshFromDataLayer: got ${buffer.count} DataItems")
            val prefs = context.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE).edit()
            try {
                buffer.forEach { item ->
                    val path = item.uri.path ?: return@forEach
                    Log.d(TAG, "refreshFromDataLayer: item path=$path")
                    val map = DataMapItem.fromDataItem(item).dataMap
                    when (path) {
                        WatchConstants.PATH_FOREX -> {
                            Log.d(TAG, "refreshFromDataLayer: FOREX eurusd=${map.getFloat(WatchConstants.KEY_EURUSD)}")
                            prefs.putFloat(WatchConstants.KEY_EURUSD, map.getFloat(WatchConstants.KEY_EURUSD))
                            prefs.putFloat(WatchConstants.KEY_EURUSD_PREV, map.getFloat(WatchConstants.KEY_EURUSD_PREV))
                            prefs.putFloat(WatchConstants.KEY_DXY, map.getFloat(WatchConstants.KEY_DXY))
                            prefs.putFloat(WatchConstants.KEY_BTC, map.getFloat(WatchConstants.KEY_BTC))
                            prefs.putLong(WatchConstants.KEY_ALERT_TS, map.getLong(WatchConstants.KEY_ALERT_TS))
                            map.getString(WatchConstants.KEY_ALERT_DIR)?.let {
                                prefs.putString(WatchConstants.KEY_ALERT_DIR, it)
                            }
                            prefs.putFloat(WatchConstants.KEY_ALERT_LEVEL, map.getFloat(WatchConstants.KEY_ALERT_LEVEL))
                        }
                        WatchConstants.PATH_YIELD -> {
                            map.getString(WatchConstants.KEY_SENTIMENT)?.let {
                                prefs.putString(WatchConstants.KEY_SENTIMENT, it)
                            }
                            prefs.putFloat(WatchConstants.KEY_YIELD_SPREAD,
                                map.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE))
                            prefs.putFloat(WatchConstants.KEY_YIELD_PREV,
                                map.getFloat(WatchConstants.KEY_YIELD_PREV, Float.MAX_VALUE))
                        }
                    }
                }
            } finally {
                buffer.release()
            }
            val committed = prefs.commit()
            Log.d(TAG, "refreshFromDataLayer: commit()=$committed")

            // Noticed from HERE, not the push listener - that path is
            // unreliable on this device, while this one runs on every
            // complication request.
            //
            // Buzz DIRECTLY first, then try to hand the repeat to
            // AlertNagService. Order matters: starting a foreground service
            // from the background is DENIED unless the app is exempt from
            // battery optimisation (verified on-device 2026-08-12 -
            // ForegroundServiceStartNotAllowedException with code:DENIED once
            // the allowlist entry was removed). Without this direct call, a
            // user who hasn't granted that exemption would get no buzz at all
            // rather than degrading to a single one.
            if (AlertBuzzer.shouldKeepNagging(context)) {
                AlertBuzzer.buzzIfNewAlert(context)
                AlertNagService.start(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshFromDataLayer: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
