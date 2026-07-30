package com.reddoor3.forexdial.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants
import kotlin.math.roundToLong

// Serves the intraday pips bar string, e.g. "▲ +52 pips · +0.44%".
// prev is the daily open baseline set by ForexSyncWorker each morning.
class EurUsdPipsComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(1.08500f, 1.08448f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS refresh - see EurUsdComplicationService for why gating on
        // ==0f was the actual bug (stale-but-present values never refreshed).
        DataLayerHelper.refreshFromDataLayer(this)
        val price = prefs.getFloat(WatchConstants.KEY_EURUSD, 0f)
        val prev  = prefs.getFloat(WatchConstants.KEY_EURUSD_PREV, 0f)
        if (price == 0f || prev == 0f) return null
        return build(price, prev)
    }

    private fun build(price: Float, prev: Float): ComplicationData {
        val diff  = (price - prev).toDouble()
        val pips  = (diff * 10_000).roundToLong()
        val pct   = diff / prev * 100.0
        val arrow = if (diff >= 0) "▲" else "▼"
        val pSign = if (pips >= 0) "+" else ""   // toLong preserves the minus sign
        val cSign = if (pct  >= 0) "+" else ""
        val text  = "$arrow ${pSign}${pips} pips · ${cSign}${"%.2f".format(pct)}%"
        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("EUR/USD intraday pips").build()
        ).build()
    }
}
