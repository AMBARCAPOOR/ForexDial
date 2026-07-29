package com.reddoor3.forexdial.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.FinnhubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** DXY index price → Duo Edge left slot. Color: #90EE90 (set in WFF). */
class DxyComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildShort("104.23")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        withContext(Dispatchers.IO) {
            try {
                val q = FinnhubClient.getDxy()
                getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putFloat(Constants.KEY_DXY, q.price.toFloat())
                    .apply()
                buildShort("%.2f".format(q.price))
            } catch (e: Exception) {
                val cached = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getFloat(Constants.KEY_DXY, 0f)
                if (cached != 0f) buildShort("%.2f".format(cached)) else null
            }
        }

    private fun buildShort(text: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("DXY").build()
        ).setTitle(PlainComplicationText.Builder("DXY").build()).build()
}
