package com.reddoor3.forexdial.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

class DxyComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build("104.23")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS refresh - see EurUsdComplicationService for why gating on
        // ==0f was the actual bug (stale-but-present values never refreshed).
        DataLayerHelper.refreshFromDataLayer(this)
        val price = prefs.getFloat(WatchConstants.KEY_DXY, 0f)
        return if (price != 0f) build("%.2f".format(price)) else null
    }

    private fun build(text: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("DXY index").build()
        ).setTitle(PlainComplicationText.Builder("DXY").build()).build()
}
