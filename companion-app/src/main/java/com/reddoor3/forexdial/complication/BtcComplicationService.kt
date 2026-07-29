package com.reddoor3.forexdial.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.FinnhubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** BTC/USD price → Duo Edge right slot. Color: #FFD700 gold (set in WFF). */
class BtcComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildShort("67420")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        withContext(Dispatchers.IO) {
            try {
                val q = FinnhubClient.getBtc()
                getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putFloat(Constants.KEY_BTC, q.price.toFloat())
                    .apply()
                // BTC displayed as whole dollars (no decimals at this scale)
                buildShort("%.0f".format(q.price))
            } catch (e: Exception) {
                val cached = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getFloat(Constants.KEY_BTC, 0f)
                if (cached != 0f) buildShort("%.0f".format(cached)) else null
            }
        }

    private fun buildShort(text: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("BTC/USD").build()
        ).setTitle(PlainComplicationText.Builder("BTC").build()).build()
}
