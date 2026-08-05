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

        if (newAlertTs != 0L) {
            vibrateForAlert()
        }

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

    // Double buzz so it's distinguishable from an ordinary system
    // notification. VIBRATE is a normal permission (granted at install, no
    // runtime prompt), so this needs nothing from the user to work.
    private fun vibrateForAlert() {
        runCatching {
            val vibrator =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as android.os.VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
            val pattern = longArrayOf(0, 250, 150, 250)
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
