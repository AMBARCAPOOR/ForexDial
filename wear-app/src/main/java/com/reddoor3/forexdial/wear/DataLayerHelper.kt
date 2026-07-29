package com.reddoor3.forexdial.wear

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object DataLayerHelper {

    suspend fun refreshFromDataLayer(context: Context) {
        try {
            val buffer = Wearable.getDataClient(context).getDataItems().await()
            val prefs = context.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE).edit()
            try {
                buffer.forEach { item ->
                    val path = item.uri.path ?: return@forEach
                    val map = DataMapItem.fromDataItem(item).dataMap
                    when (path) {
                        WatchConstants.PATH_FOREX -> {
                            prefs.putFloat(WatchConstants.KEY_EURUSD, map.getFloat(WatchConstants.KEY_EURUSD))
                            prefs.putFloat(WatchConstants.KEY_EURUSD_PREV, map.getFloat(WatchConstants.KEY_EURUSD_PREV))
                            prefs.putFloat(WatchConstants.KEY_DXY, map.getFloat(WatchConstants.KEY_DXY))
                            prefs.putFloat(WatchConstants.KEY_BTC, map.getFloat(WatchConstants.KEY_BTC))
                        }
                        WatchConstants.PATH_YIELD -> {
                            map.getString(WatchConstants.KEY_SENTIMENT)?.let {
                                prefs.putString(WatchConstants.KEY_SENTIMENT, it)
                            }
                            prefs.putFloat(WatchConstants.KEY_YIELD_SPREAD,
                                map.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE))
                        }
                    }
                }
            } finally {
                buffer.release()
            }
            prefs.commit()
        } catch (_: Exception) {}
    }
}
