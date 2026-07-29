package com.reddoor3.forexdial.complication

import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.session.MarketSessionCalculator

/**
 * NYSE session status → Circle (bottom-left) complication slot.
 *
 * Pushes numeric string "0" / "1" / "2" so WFF can apply conditional colors:
 *   "0" → closed  → red   #E53935
 *   "1" → pre/post → amber #FFB300
 *   "2" → open    → green #00C87A
 */
class NyseSessionComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildStatus("2", "NYSE")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val status = MarketSessionCalculator.getNyseStatus()
        return buildStatus(status.value.toString(), "NYSE")
    }

    private fun buildStatus(value: String, label: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(value).build(),
            contentDescription = PlainComplicationText.Builder("$label session").build()
        ).setTitle(PlainComplicationText.Builder(label).build()).build()
}
