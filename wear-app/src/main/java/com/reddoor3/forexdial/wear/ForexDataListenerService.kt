package com.reddoor3.forexdial.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.reddoor3.forexdial.wear.complication.*

class ForexDataListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val store = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val prefs = store.edit()
        var forexChanged = false
        var yieldChanged = false
        var newAlertTs = 0L

        events.forEach { event ->
            val path = event.dataItem.uri.path ?: return@forEach
            val map  = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (path) {
                WatchConstants.PATH_FOREX -> {
                    prefs.putFloat(WatchConstants.KEY_EURUSD,      map.getFloat(WatchConstants.KEY_EURUSD))
                    prefs.putFloat(WatchConstants.KEY_EURUSD_PREV, map.getFloat(WatchConstants.KEY_EURUSD_PREV))
                    prefs.putFloat(WatchConstants.KEY_DXY,         map.getFloat(WatchConstants.KEY_DXY))
                    prefs.putFloat(WatchConstants.KEY_BTC,         map.getFloat(WatchConstants.KEY_BTC))

                    // Buzz only for an alert we've never seen before - the
                    // phone re-sends the same fired alert on every 3-minute
                    // sync until a new crossing replaces it, so comparing
                    // against the stored ts is what stops it vibrating over
                    // and over for one crossing.
                    val incomingAlertTs = map.getLong(WatchConstants.KEY_ALERT_TS)
                    val knownAlertTs    = store.getLong(WatchConstants.KEY_ALERT_TS, 0L)
                    if (incomingAlertTs != 0L && incomingAlertTs != knownAlertTs) {
                        newAlertTs = incomingAlertTs
                    }
                    prefs.putLong(WatchConstants.KEY_ALERT_TS, incomingAlertTs)
                    map.getString(WatchConstants.KEY_ALERT_DIR)?.let {
                        prefs.putString(WatchConstants.KEY_ALERT_DIR, it)
                    }
                    prefs.putFloat(WatchConstants.KEY_ALERT_LEVEL, map.getFloat(WatchConstants.KEY_ALERT_LEVEL))
                    forexChanged = true
                }
                WatchConstants.PATH_YIELD -> {
                    prefs.putString(WatchConstants.KEY_SENTIMENT,   map.getString(WatchConstants.KEY_SENTIMENT))
                    prefs.putFloat(WatchConstants.KEY_YIELD_SPREAD, map.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE))
                    prefs.putFloat(WatchConstants.KEY_YIELD_PREV,   map.getFloat(WatchConstants.KEY_YIELD_PREV, Float.MAX_VALUE))
                    yieldChanged = true
                }
                WatchConstants.PATH_SUN -> {
                    prefs.putLong(WatchConstants.KEY_SUNRISE, map.getLong(WatchConstants.KEY_SUNRISE))
                    prefs.putLong(WatchConstants.KEY_SUNSET,  map.getLong(WatchConstants.KEY_SUNSET))
                }
            }
        }
        prefs.commit()

        // Route through the same guard the polling path uses, so whichever
        // path sees the new alert first buzzes and the other doesn't repeat
        // it. newAlertTs is no longer needed to gate this - AlertBuzzer does
        // its own already-buzzed check - but it still tells us a push
        // genuinely arrived, which is worth knowing given how rarely it does.
        if (newAlertTs != 0L) {
            android.util.Log.d("FDD_Listener", "push delivered alert ts=$newAlertTs")
        }
        AlertBuzzer.buzzIfNewAlert(this)

        if (forexChanged) {
            listOf(
                EurUsdComplicationService::class.java,
                EurUsdPipComplicationService::class.java,
                EurUsdPipsComplicationService::class.java,
                DxyComplicationService::class.java,
                BtcComplicationService::class.java,
                AlertComplicationService::class.java
            ).forEach { cls ->
                runCatching {
                    ComplicationDataSourceUpdateRequester
                        .create(this, ComponentName(this, cls))
                        .requestUpdateAll()
                }
            }
        }
        if (yieldChanged) {
            listOf(
                YieldSpreadComplicationService::class.java,
                YieldDirectionComplicationService::class.java
            ).forEach { cls ->
                runCatching {
                    ComplicationDataSourceUpdateRequester
                        .create(this, ComponentName(this, cls))
                        .requestUpdateAll()
                }
            }
        }
    }

}
