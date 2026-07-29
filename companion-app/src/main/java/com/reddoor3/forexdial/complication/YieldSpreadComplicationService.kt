package com.reddoor3.forexdial.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.reddoor3.forexdial.Constants

class YieldSpreadComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build("LONG", -1.4893f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val prefs  = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val dir    = prefs.getString(Constants.KEY_YIELD_SENT, "NEUT") ?: "NEUT"
        val spread = prefs.getFloat("yield_spread_cached", Float.MAX_VALUE)
        return build(dir, spread)
    }

    private fun build(dir: String, spread: Float): ComplicationData {
        val text = if (spread != Float.MAX_VALUE) {
            val sign = if (spread >= 0) "+" else "−"
            "%s%.2f".format(sign, Math.abs(spread))
        } else dir
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("DE-US yield spread").build()
        ).setTitle(PlainComplicationText.Builder("YIELD").build()).build()
    }
}
