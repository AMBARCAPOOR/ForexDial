package com.reddoor3.forexdial.wear.complication

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants

// Alert symbol - a hand-drawn cyan arrow up / orange arrow down (see build()
// for why this ended up hand-drawn rather than emoji). Mirrors the battery
// candle's slot (see watchface.xml battery_group, x=48,y=215,32x129) on the
// opposite side of the clock/date block. Ambar corrected the position
// 2026-08-05 after the first pass wrongly put it inside
// EurUsdComplicationService, in the rate row rather than the clock row -
// kept as its own complication (rather than folded into an existing one)
// since none of the existing slots cover this part of the face, and it
// needs the same live price/prev data EurUsdComplicationService reads to
// pick the direction.
//
// TEMPORARY: alertTriggered is hardcoded true so fit can be verified
// on-device before B.4 wires in real price-threshold detection - this is
// NOT yet gated by any actual alert condition.
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
        // Ambar 2026-08-05 (round 4): no more emoji at all - font glyphs
        // (plain triangles AND full emoji) kept producing surprises (wrong
        // apparent size, wrong-looking animal). Hand-drawn instead: a proper
        // arrow with a head AND a shaft/tail, elevator-indicator style, so
        // there's total control over proportions and it always renders
        // identically. Coloured cyan/orange to match the same up/down
        // convention used everywhere else on the face.
        val bw = 44; val bh = 129
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        if (alertTriggered) {
            val canvas = Canvas(bitmap)
            val color = if (rising) Color.parseColor("#00e5ff") else Color.parseColor("#FF7700")
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            val cx = bw / 2f
            val headHalfWidth = 14f
            val stemHalfWidth = 6f
            if (rising) {
                val apexY = 22f; val baseY = 66f; val stemBottomY = 106f
                val head = Path().apply {
                    moveTo(cx, apexY)
                    lineTo(cx - headHalfWidth, baseY)
                    lineTo(cx + headHalfWidth, baseY)
                    close()
                }
                canvas.drawPath(head, arrowPaint)
                canvas.drawRect(cx - stemHalfWidth, baseY, cx + stemHalfWidth, stemBottomY, arrowPaint)
            } else {
                val stemTopY = 23f; val baseY = 63f; val apexY = 107f
                canvas.drawRect(cx - stemHalfWidth, stemTopY, cx + stemHalfWidth, baseY, arrowPaint)
                val head = Path().apply {
                    moveTo(cx, apexY)
                    lineTo(cx - headHalfWidth, baseY)
                    lineTo(cx + headHalfWidth, baseY)
                    close()
                }
                canvas.drawPath(head, arrowPaint)
            }
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
