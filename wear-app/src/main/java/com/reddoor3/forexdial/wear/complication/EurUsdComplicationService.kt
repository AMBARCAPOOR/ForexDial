package com.reddoor3.forexdial.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.Log
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.RefreshRequestReceiver
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

        // Ambar 2026-07-30: two boxes, not one - a box around the rate, a
        // separate smaller one around the pips line. Both coloured by
        // pipColor (already computed above for the pip digits), so this is
        // "A" (the boxes) and "B.2" (direction-coloured border) in one pass -
        // no separate WFF Condition needed since the colour logic already
        // lives right here in Kotlin.
        //
        // Horizontal room is deliberate, not incidental: the battery candle
        // sits at absolute screen x=33-65 (this bitmap starts at screen
        // x=9, so that's local x=24-56) - box 1 starts well clear of it at
        // local x=70. The right side stops well short of the bitmap's own
        // edge (432) to reserve space for the alert symbol (bull/bear+fire,
        // still to be built and verified on-device - not drawn yet).
        // Ambar 2026-08-05: both boxes were fixed pixel rectangles that never
        // tracked the text they were meant to frame - startX (rate) and the
        // pips bar's centred x both shift with every live value's width, so
        // a static box clipped the outer digits whenever the live text ran
        // wider than the guessed rectangle. Fixed below by measuring each
        // block's real rendered pixel bounds (getTextBounds, not
        // measureText - advance width can undershoot true bold-glyph edges)
        // and deriving each box FROM that measurement instead of a guess.
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.SQUARE
            color = pipColor
        }

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

        // Box 1: true left/right pixel edges of the combined main+pip block.
        // Ambar 2026-08-05 (round 2): widened another 25px each side on top
        // of the previous 5px pad (30px total from the true text edges), and
        // the two VERTICAL edges only made 400% thicker (3f -> 15f) for a
        // bracket-style frame - top/bottom stay the original thin 3f line.
        // drawRect can't vary stroke width per side, so this is four
        // drawLine calls instead of one drawRect; Cap.SQUARE on both paints
        // extends each line half its own width past its endpoints, which
        // closes the corners cleanly despite the thickness mismatch.
        val rateLeft   = startX + mainBounds.left - 30f
        val rateRight  = startX + mainW + pipBounds.right + 30f
        val rateTop    = 8f
        val rateBottom = 113f
        val boxEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 15f
            strokeCap = Paint.Cap.SQUARE
            color = pipColor
        }
        canvas.drawLine(rateLeft, rateTop, rateRight, rateTop, boxPaint)        // top, thin
        canvas.drawLine(rateLeft, rateBottom, rateRight, rateBottom, boxPaint)  // bottom, thin
        canvas.drawLine(rateLeft, rateTop, rateLeft, rateBottom, boxEdgePaint)    // left edge, thick
        canvas.drawLine(rateRight, rateTop, rateRight, rateBottom, boxEdgePaint)  // right edge, thick

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
            // Ambar 2026-08-05: line moved down 5px (was 136f) per instruction.
            val barY = 141f

            // Box 2: was a fixed guess (130,120)-(302,148) that didn't track
            // the bar's actual centred width, so it clipped whenever the
            // live pips/percent text ran wider than that guess. Now measured
            // from the real string each render and padded 5px on ALL four
            // sides ("bigger than that line by 5px each side").
            //
            // getTextBounds() IGNORES Paint.textAlign - it always measures as
            // if the text were drawn LEFT-aligned starting at x=0, regardless
            // of the CENTER alignment barPaint actually draws with. Using
            // bw/2f directly as that reference (as if it were the left edge)
            // put the box roughly one half-text-width too far right - caught
            // on-device as a box skewed off to the right of the real text.
            // Fix: derive the equivalent left-aligned draw x from the
            // measured width first, then add the bounds to THAT.
            val barW = barPaint.measureText(bar)
            val barDrawX = bw / 2f - barW / 2f
            val barBounds = Rect()
            barPaint.getTextBounds(bar, 0, bar.length, barBounds)
            val barLeft   = barDrawX + barBounds.left
            val barRight  = barDrawX + barBounds.right
            val barTop       = barY + barBounds.top
            val barBottomRaw = barY + barBounds.bottom
            // Ambar 2026-08-05: bottom was getting cut off. Not the
            // DigitalClock - that's a separate WFF element starting at
            // absolute screen y=234, a clean 4px below this complication's
            // own y=80+150=230 bottom edge, so it can never overlap this
            // bitmap. The real cause: this bitmap is only bh=150px tall, and
            // barY(141) + the text's true descender bounds + the 5px pad
            // pushed the box's bottom coordinate PAST 150 - the BITMAP's own
            // edge was silently clipping it. Clamped so the box can never
            // exceed the bitmap regardless of exact glyph metrics.
            val barBottom = (barBottomRaw + 5f).coerceAtMost(bh - 2f)
            canvas.drawRect(barLeft - 5f, barTop - 5f, barRight + 5f, barBottom, boxPaint)

            canvas.drawText(bar, bw / 2f, barY, barPaint)
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = Icon.createWithBitmap(bitmap),
                type = SmallImageType.PHOTO
            ).build(),
            contentDescription = PlainComplicationText.Builder("EUR/USD $priceStr").build()
        ).setTapAction(refreshTapAction()).build()
    }

    // Tap-to-refresh (2026-07-30): asks the phone for a fresh sync rather
    // than doing nothing, which was the previous behaviour (confirmed by
    // grep: no complication anywhere had a TapAction set).
    private fun refreshTapAction(): PendingIntent =
        PendingIntent.getBroadcast(
            this, 0,
            Intent(this, RefreshRequestReceiver::class.java).setAction(RefreshRequestReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
