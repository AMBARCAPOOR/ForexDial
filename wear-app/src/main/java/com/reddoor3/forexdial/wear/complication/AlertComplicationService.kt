package com.reddoor3.forexdial.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.AlertDismissReceiver
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
// Shown ONLY while a price alert is actually live: it appears when the phone
// reports a threshold crossing and disappears when the user taps it (Ambar
// 2026-08-05 - "it cant be there the whole time"). Between those, this
// returns a fully transparent bitmap, so the WFF flash animation simply
// blinks nothing.
class AlertComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(rising = true, alertActive = true)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        DataLayerHelper.refreshFromDataLayer(this)

        val alertTs   = prefs.getLong(WatchConstants.KEY_ALERT_TS, 0L)
        val dismissed = prefs.getLong(WatchConstants.KEY_ALERT_DISMISSED_TS, 0L)
        val alertActive = alertTs != 0L && alertTs != dismissed

        // Direction comes from the crossing itself, not from the current
        // intraday tick - the arrow should keep showing which way price
        // crossed the threshold, even if it wobbles back afterwards.
        val rising = when (prefs.getString(WatchConstants.KEY_ALERT_DIR, "") ?: "") {
            "UP"   -> true
            "DOWN" -> false
            else   -> prefs.getFloat(WatchConstants.KEY_EURUSD, 0f) >=
                      prefs.getFloat(WatchConstants.KEY_EURUSD_PREV, 0f)
        }
        return build(rising, alertActive)
    }

    private fun build(rising: Boolean, alertActive: Boolean): ComplicationData {
        val alertTriggered = alertActive
        // Ambar 2026-08-05 (round 4): no more emoji at all - font glyphs
        // (plain triangles AND full emoji) kept producing surprises (wrong
        // apparent size, wrong-looking animal). Hand-drawn instead: a proper
        // arrow with a head AND a shaft/tail, elevator-indicator style, so
        // there's total control over proportions and it always renders
        // identically. Coloured cyan/orange to match the same up/down
        // convention used everywhere else on the face.
        //
        // Ambar 2026-08-05 (round 5): added a bell above the arrow (also
        // hand-drawn, same reasoning) - the bell means "an alert is active"
        // generically (fixed gold colour, not direction-coded), the arrow
        // below it means "which way it crossed" (cyan/orange). Arrow shrunk
        // vertically (84px -> 70px) to make room without widening the slot
        // further.
        val bw = 44; val bh = 129
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        if (alertTriggered) {
            val canvas = Canvas(bitmap)
            val cx = bw / 2f

            val bellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#FFD700")
            }
            val bellTop = 6f; val bellBottom = 36f
            val bellHalfWidthTop = 3f; val bellHalfWidthBottom = 13f
            val bell = Path().apply {
                moveTo(cx - bellHalfWidthTop, bellTop)
                quadTo(cx - bellHalfWidthBottom, bellBottom - 12f, cx - bellHalfWidthBottom, bellBottom)
                lineTo(cx + bellHalfWidthBottom, bellBottom)
                quadTo(cx + bellHalfWidthBottom, bellBottom - 12f, cx + bellHalfWidthTop, bellTop)
                close()
            }
            canvas.drawPath(bell, bellPaint)
            canvas.drawRect(cx - bellHalfWidthBottom - 2f, bellBottom, cx + bellHalfWidthBottom + 2f, bellBottom + 4f, bellPaint)
            canvas.drawCircle(cx, bellBottom + 10f, 3f, bellPaint) // clapper
            canvas.drawCircle(cx, bellTop - 2f, 2f, bellPaint)     // hanger knob

            val color = if (rising) Color.parseColor("#00e5ff") else Color.parseColor("#FF7700")
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            val headHalfWidth = 14f
            val stemHalfWidth = 6f
            if (rising) {
                val apexY = 58f; val baseY = 94f; val stemBottomY = 124f
                val head = Path().apply {
                    moveTo(cx, apexY)
                    lineTo(cx - headHalfWidth, baseY)
                    lineTo(cx + headHalfWidth, baseY)
                    close()
                }
                canvas.drawPath(head, arrowPaint)
                canvas.drawRect(cx - stemHalfWidth, baseY, cx + stemHalfWidth, stemBottomY, arrowPaint)
            } else {
                val stemTopY = 58f; val baseY = 88f; val apexY = 124f
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

        val builder = SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithBitmap(bitmap),
                type = SmallImageType.PHOTO
            ).build(),
            contentDescription = PlainComplicationText.Builder(
                if (!alertActive) "No active alert"
                else if (rising) "Price alert: crossed up" else "Price alert: crossed down"
            ).build()
        )
        // Only tappable while there's actually something to clear.
        if (alertActive) builder.setTapAction(dismissTapAction())
        return builder.build()
    }

    private fun dismissTapAction(): PendingIntent =
        PendingIntent.getBroadcast(
            this, 0,
            Intent(this, AlertDismissReceiver::class.java)
                .setAction(AlertDismissReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
