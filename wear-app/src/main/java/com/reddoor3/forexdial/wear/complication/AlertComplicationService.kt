package com.reddoor3.forexdial.wear.complication

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

// Alert symbol - bull+fire (up) / bear+fire (down). Mirrors the battery
// candle's slot (see watchface.xml battery_group, x=48,y=215,32x129) on the
// opposite side of the clock/date block. Ambar corrected this 2026-08-05
// after the first pass wrongly put it inside EurUsdComplicationService, in
// the rate row rather than the clock row - kept as its own complication
// (rather than folded into an existing one) since none of the existing
// slots cover this part of the face, and it needs the same live
// price/prev data EurUsdComplicationService reads to pick bull vs bear.
//
// TEMPORARY: alertTriggered is hardcoded true so on-device emoji rendering
// and fit can be verified before B.4 wires in real price-threshold
// detection - this is NOT yet gated by any actual alert condition.
class AlertComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(rising = true)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        DataLayerHelper.refreshFromDataLayer(this)
        val price = prefs.getFloat(WatchConstants.KEY_EURUSD, 0f)
        val prev  = prefs.getFloat(WatchConstants.KEY_EURUSD_PREV, 0f)
        if (price == 0f) return null
        return build(rising = price >= prev)
    }

    private fun build(rising: Boolean): ComplicationData {
        val alertTriggered = true
        val bw = 32; val bh = 129
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        if (alertTriggered) {
            val canvas = Canvas(bitmap)
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            val animal = if (rising) "🐂" else "🐻" // bull / bear
            canvas.drawText(animal, bw / 2f, 48f, emojiPaint)
            canvas.drawText("🔥", bw / 2f, 96f, emojiPaint) // fire
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithBitmap(bitmap),
                type = SmallImageType.PHOTO
            ).build(),
            contentDescription = PlainComplicationText.Builder(if (rising) "Bull alert" else "Bear alert").build()
        ).build()
    }
}
