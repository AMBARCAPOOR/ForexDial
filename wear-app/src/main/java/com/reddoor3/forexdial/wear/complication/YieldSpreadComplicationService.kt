package com.reddoor3.forexdial.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

class YieldSpreadComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(-1.4893f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS refresh - see EurUsdComplicationService for why gating on
        // "missing" was the actual bug (stale-but-present values never refreshed).
        DataLayerHelper.refreshFromDataLayer(this)
        val spread = prefs.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE)
        return if (spread != Float.MAX_VALUE) build(spread) else null
    }

    private fun build(spread: Float): ComplicationData {
        // e.g. "−1.49" — fits within SHORT_TEXT 7-char limit
        val sign = if (spread >= 0) "+" else "−"
        val text = "%s%.2f".format(sign, Math.abs(spread))
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("DE-US yield spread").build()
        ).setTitle(PlainComplicationText.Builder("YIELD").build()).build()
    }
}
