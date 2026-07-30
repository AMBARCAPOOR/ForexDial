package com.reddoor3.forexdial.wear.complication

import android.content.Context
import android.graphics.*
import android.util.Log
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.WatchConstants
import kotlin.math.roundToLong

class EurUsdComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SMALL_IMAGE -> buildImage(1.08473f, 1.07000f)
            else -> buildForType(type, "1.085")
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d("FDD_EurUsdComp", "onComplicationRequest: ENTER type=${request.complicationType}")
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS pull from the Data Layer directly, not just when the cached
        // value is missing (0f). The phone's "wake up and check" push
        // notification is unreliable under normal Android background
        // restrictions (confirmed 2026-07-29: GMS logs "Failed to deliver
        // message" for every app on the watch, not just this one), but the
        // DataItem itself IS reliably stored - refreshFromDataLayer queries
        // that storage directly. Gating on ==0f meant stale-but-present
        // values NEVER got refreshed, which was the actual bug.
        DataLayerHelper.refreshFromDataLayer(this)
        val price = prefs.getFloat(WatchConstants.KEY_EURUSD, 0f)
        val prev  = prefs.getFloat(WatchConstants.KEY_EURUSD_PREV, 0f)
        if (price == 0f) return null

        return when (request.complicationType) {
            ComplicationType.SMALL_IMAGE -> buildImage(price, prev)
            // SHORT_TEXT: main rate body only ("1.085"), pip digits served by EurUsdPipComplicationService
            else -> buildForType(request.complicationType, "%.5f".format(price).dropLast(2))
        }
    }

    private fun buildImage(price: Float, prev: Float): ComplicationData {
        val rising   = price >= prev
        val priceStr = "%.5f".format(price)
        val main     = priceStr.dropLast(2)
        val pip      = priceStr.takeLast(2)   // pip + pipette digits only, no arrow
        val pipColor = if (rising) Color.parseColor("#00e5ff") else Color.parseColor("#FF7700")

        // EUR/USD is the focal element of the face (v3 spec slot map: Center /
        // LargeBox), so it is drawn larger than the clock. Sizes set by Ambar
        // 2026-07-29: 90px body, 96px pip+pipette. Bitmap height must clear the
        // larger text, and the WFF slot height must match this bitmap.
        //
        // The pips bar is drawn into THIS bitmap rather than its own slot: it
        // costs no extra complication slot (the budget is 8 and the design needs
        // all of them), and it guarantees the arrow/pips/percent are computed
        // from the same price/prev in the same pass, so they can never disagree
        // with the rate's direction colour or lag it on a separate refresh.
        val bw = 432; val bh = 150
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val tf = Typeface.create("sans-serif-condensed", Typeface.BOLD)

        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = 90f
            color = Color.WHITE
        }
        val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = 96f
            color = pipColor
        }

        val mainW  = mainPaint.measureText(main)
        val pipW   = pipPaint.measureText(pip)
        val startX = (bw - mainW - pipW) / 2f
        val base   = 94f

        // The pip digits are larger than the body, so they must be aligned on a
        // shared CENTRE LINE, not a shared baseline. On a shared baseline the
        // taller glyphs only grow upward and read as sitting too high.
        // Measure real glyph bounds (digits have no descenders, so font metrics
        // would overstate the box) and offset the pip baseline so both blocks
        // share the same vertical midpoint.
        val mainBounds = Rect()
        val pipBounds  = Rect()
        mainPaint.getTextBounds(main, 0, main.length, mainBounds)
        pipPaint.getTextBounds(pip, 0, pip.length, pipBounds)

        // Glyph midpoint relative to the baseline (negative = above it).
        val mainMid = (mainBounds.top + mainBounds.bottom) / 2f
        val pipMid  = (pipBounds.top + pipBounds.bottom) / 2f
        val pipBase = base + (mainMid - pipMid)

        canvas.drawText(main, startX, base, mainPaint)
        canvas.drawText(pip, startX + mainW, pipBase, pipPaint)

        // Pips bar, e.g. "▲ +52 pips · +0.44%", same direction colour as the
        // pip digits. Only drawn once a daily-open baseline exists.
        if (prev > 0f) {
            val diff  = (price - prev).toDouble()
            val pips  = (diff * 10_000).roundToLong()
            val pct   = diff / prev * 100.0
            val arrow = if (diff >= 0) "▲" else "▼"
            val bar   = "$arrow ${if (pips >= 0) "+" else ""}$pips pips · " +
                        "${if (pct >= 0) "+" else ""}${"%.2f".format(pct)}%"

            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = 28f
                color = pipColor
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(bar, bw / 2f, 136f, barPaint)
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithBitmap(bitmap),
                type = SmallImageType.PHOTO
            ).build(),
            contentDescription = PlainComplicationText.Builder("EUR/USD $priceStr").build()
        ).build()
    }

    private fun buildForType(type: ComplicationType, text: String): ComplicationData? =
        when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder("EUR/USD rate").build()
            ).build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder("EUR/USD rate").build()
            ).build()

            else -> null
        }
}
