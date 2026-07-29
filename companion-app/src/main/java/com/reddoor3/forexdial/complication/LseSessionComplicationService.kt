package com.reddoor3.forexdial.complication

import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.session.MarketSessionCalculator

/** LSE session status → Circle (bottom-right) complication slot. See NyseSessionComplicationService for value mapping. */
class LseSessionComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildStatus("2", "LSE")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val status = MarketSessionCalculator.getLseStatus()
        return buildStatus(status.value.toString(), "LSE")
    }

    private fun buildStatus(value: String, label: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(value).build(),
            contentDescription = PlainComplicationText.Builder("$label session").build()
        ).setTitle(PlainComplicationText.Builder(label).build()).build()
}
