package com.reddoor3.forexdial.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

// Direction flag for WFS conditional layers.
// Sends "L" (LONG/bullish) or "S" (SHORT/bearish) as SHORT_TEXT.
// In WFS: bind two EUR/USD box border layers to this via
//   [COMPLICATION_X_SHORT_TEXT] == "L"  →  show cyan border
//   [COMPLICATION_X_SHORT_TEXT] == "S"  →  show orange border
class YieldDirectionComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build("L")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(WatchConstants.KEY_SENTIMENT, null) == null) {
            DataLayerHelper.refreshFromDataLayer(this)
        }
        val sent = prefs.getString(WatchConstants.KEY_SENTIMENT, null) ?: return null
        val flag = when (sent) {
            "LONG"  -> "L"
            "SHORT" -> "S"
            else    -> "N"
        }
        return build(flag)
    }

    private fun build(flag: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(flag).build(),
            contentDescription = PlainComplicationText.Builder("Yield direction flag").build()
        ).build()
}
