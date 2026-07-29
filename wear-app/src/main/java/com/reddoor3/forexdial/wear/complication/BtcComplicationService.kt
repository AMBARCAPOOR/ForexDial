package com.reddoor3.forexdial.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

class BtcComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build("67420")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        if (prefs.getFloat(WatchConstants.KEY_BTC, 0f) == 0f) {
            DataLayerHelper.refreshFromDataLayer(this)
        }
        val price = prefs.getFloat(WatchConstants.KEY_BTC, 0f)
        return if (price != 0f) build("%.0f".format(price)) else null
    }

    private fun build(text: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("BTC/USD price").build()
        ).setTitle(PlainComplicationText.Builder("BTC").build()).build()
}
