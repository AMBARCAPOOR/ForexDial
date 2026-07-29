package com.reddoor3.forexdial.complication

import android.content.Context
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.Constants
import com.reddoor3.forexdial.api.FinnhubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provides EUR/USD price to the LargeBox complication slot.
 *
 * Format: "1.08473" — 5 decimal places as a full string.
 * WFF handles per-char coloring:
 *   chars 1-5 → white (#F0F0F0)
 *   chars 6-7 → blue (#4FC3F7) if up, orange (#FF6B00) if down
 *   char  8   → pink (#FF69B4) always
 *
 * UPDATE_PERIOD_SECONDS=30 is declared in AndroidManifest.
 */
class EurUsdComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildLongText("1.08473", "EUR/USD")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        withContext(Dispatchers.IO) {
            try {
                val q = FinnhubClient.getEurUsd()
                // Cache for other uses (e.g., direction indicator)
                getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putFloat(Constants.KEY_EURUSD, q.price.toFloat())
                    .putFloat(Constants.KEY_EURUSD_PREV, q.prevClose.toFloat())
                    .apply()

                val priceStr = "%.5f".format(q.price)   // e.g. "1.08473"
                val title    = if (q.isUp) "EUR/USD ▲" else "EUR/USD ▼"
                buildLongText(priceStr, title)
            } catch (e: Exception) {
                // Return stale cached value if available
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val cached = prefs.getFloat(Constants.KEY_EURUSD, 0f)
                if (cached != 0f) buildLongText("%.5f".format(cached), "EUR/USD") else null
            }
        }

    private fun buildLongText(text: String, title: String): ComplicationData =
        LongTextComplicationData.Builder(
            text  = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(title).build()
        ).setTitle(PlainComplicationText.Builder(title).build()).build()
}
