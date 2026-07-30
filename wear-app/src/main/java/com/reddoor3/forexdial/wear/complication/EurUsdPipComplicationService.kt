package com.reddoor3.forexdial.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

// Serves the last 2 digits (pip + pipette) of the EUR/USD rate, e.g. "74".
// Displayed in cyan (#00e5ff) when price is rising, orange (#FF7700) when falling.
// The color must be set per-direction using two conditional text layers in WFS.
class EurUsdPipComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build("74")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS refresh - see EurUsdComplicationService for why gating on
        // ==0f was the actual bug (stale-but-present values never refreshed).
        DataLayerHelper.refreshFromDataLayer(this)
        val price = prefs.getFloat(WatchConstants.KEY_EURUSD, 0f)
        if (price == 0f) return null
        val pip = "%.5f".format(price).takeLast(2)
        return build(pip)
    }

    private fun build(pip: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(pip).build(),
            contentDescription = PlainComplicationText.Builder("EUR/USD pip digits").build()
        ).build()
}
