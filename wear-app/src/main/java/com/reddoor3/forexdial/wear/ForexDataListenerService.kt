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
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE).edit()
        var forexChanged = false
        var yieldChanged = false

        events.forEach { event ->
            val path = event.dataItem.uri.path ?: return@forEach
            val map  = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (path) {
                WatchConstants.PATH_FOREX -> {
                    prefs.putFloat(WatchConstants.KEY_EURUSD,      map.getFloat(WatchConstants.KEY_EURUSD))
                    prefs.putFloat(WatchConstants.KEY_EURUSD_PREV, map.getFloat(WatchConstants.KEY_EURUSD_PREV))
                    prefs.putFloat(WatchConstants.KEY_DXY,         map.getFloat(WatchConstants.KEY_DXY))
                    prefs.putFloat(WatchConstants.KEY_BTC,         map.getFloat(WatchConstants.KEY_BTC))
                    forexChanged = true
                }
                WatchConstants.PATH_YIELD -> {
                    prefs.putString(WatchConstants.KEY_SENTIMENT,   map.getString(WatchConstants.KEY_SENTIMENT))
                    prefs.putFloat(WatchConstants.KEY_YIELD_SPREAD, map.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE))
                    yieldChanged = true
                }
                WatchConstants.PATH_SUN -> {
                    prefs.putLong(WatchConstants.KEY_SUNRISE, map.getLong(WatchConstants.KEY_SUNRISE))
                    prefs.putLong(WatchConstants.KEY_SUNSET,  map.getLong(WatchConstants.KEY_SUNSET))
                }
            }
        }
        prefs.commit()

        if (forexChanged) {
            listOf(
                EurUsdComplicationService::class.java,
                EurUsdPipComplicationService::class.java,
                EurUsdPipsComplicationService::class.java,
                DxyComplicationService::class.java,
                BtcComplicationService::class.java
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
