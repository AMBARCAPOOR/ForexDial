package com.reddoor3.forexdial.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
import com.reddoor3.forexdial.wear.RefreshRequestReceiver
import com.reddoor3.forexdial.wear.WatchConstants

// DXY | YIELD | BTC in one bitmap/one slot rather than three, so the row
// always renders as a unit and the 8-slot budget has room for the rest of
// the design. See EurUsdComplicationService for the same reasoning.
//
// DXY and BTC are drawn NEUTRAL WHITE, not direction-coloured: there is no
// stored previous value for either on the watch, and DXY is derived from the
// same once-daily Frankfurter feed as EUR/USD, so it cannot show real
// intraday direction even if a baseline existed. YIELD *is* coloured, since
// KEY_SENTIMENT comes from a separate worker with its own data path.
class TopRowComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(101.14f, -1.59f, -1.62f, 67420f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS refresh - see EurUsdComplicationService for why gating on
        // ==0f was the actual bug (stale-but-present values never refreshed).
        DataLayerHelper.refreshFromDataLayer(this)
        val dxy   = prefs.getFloat(WatchConstants.KEY_DXY, 0f)
        val yield = prefs.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE)
        val yPrev = prefs.getFloat(WatchConstants.KEY_YIELD_PREV, Float.MAX_VALUE)
        val btc   = prefs.getFloat(WatchConstants.KEY_BTC, 0f)
        if (dxy == 0f && btc == 0f) return null
        return build(dxy, yield, yPrev, btc)
    }

    private fun build(dxy: Float, yield: Float, yieldPrev: Float, btc: Float): ComplicationData {
        val bw = 432; val bh = 60
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tf      = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val tfLight = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

        // Ambar 2026-08-05: back to the numeric spread, but coloured by
        // DAY-OVER-DAY CHANGE rather than by level.
        //
        // Spread is EUR 2Y minus US 2Y, so it ROSE = EUR's rate advantage
        // improved = EUR-positive = cyan; FELL = USD-positive = orange.
        // That keeps cyan meaning the same EUR/USD-up thing it means
        // everywhere else on this face. Avoiding "widened/narrowed" here on
        // purpose - the spread is negative, so those words are ambiguous
        // (more negative is a WIDER US premium but a LOWER spread value).
        //
        // Note this is a different meaning from the LONG/SHORT word it
        // replaces: that described where the spread SITS, this describes
        // which way it MOVED. Grey when there's no previous day to compare
        // against, rather than implying a direction we don't know.
        //
        // A number is honest here now in a way it wasn't before: the old
        // source was monthly (and was running 2 months stale), so a precise
        // figure implied freshness it didn't have and a daily change was
        // not even computable. Both legs are daily as of 2026-08-05.
        val haveYield = yield != Float.MAX_VALUE
        val havePrev  = yieldPrev != Float.MAX_VALUE
        val sentColor = when {
            !haveYield || !havePrev  -> Color.LTGRAY
            yield > yieldPrev        -> Color.parseColor("#00e5ff")
            yield < yieldPrev        -> Color.parseColor("#FF7700")
            else                     -> Color.LTGRAY   // genuinely unchanged
        }

        // Sizes set by Ambar 2026-07-29: labels +50% (14->21), values +20% (22->26).
        fun labelPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfLight; textSize = 21f; this.color = color; textAlign = Paint.Align.CENTER
        }
        fun valuePaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = 26f; this.color = color; textAlign = Paint.Align.CENTER
        }

        // Ambar 2026-07-30: yield to 4dp, BTC to full 5-digit price (no "k"
        // rounding). Both are now visibly wider than when the 0.29/0.71
        // spacing was tuned, so positions widen again - asymmetrically, since
        // BTC's text grows far more ("64k" -> "64009") than DXY's format
        // (unchanged). "doesn't have to be symmetrical" per Ambar.
        val xDxy = bw * 0.27f; val xYield = bw * 0.5f; var xBtc = bw * 0.75f

        // 4dp per Ambar 2026-08-05. Unlike the earlier 4dp format (which sat
        // on a monthly, 2-month-stale series and so implied precision the
        // data didn't have), the underlying legs are now daily, so the extra
        // digits are real - they resolve sub-basis-point daily moves that
        // 2dp rounded away entirely.
        val yStr = if (haveYield) "%.4f".format(yield) else "—"
        val btcStr = if (btc > 0f) "%.0f".format(btc) else null

        // Ambar 2026-07-30: BTC moved left by 25% of the gap between yield's
        // last digit and BTC's first digit - twice now (second pass applies
        // the same 25%-of-remaining-gap operation again, using the gap AFTER
        // the first shift, not a doubled formula). Measured from actual
        // glyph widths (Paint.measureText) each time, not a fixed offset,
        // since both strings' widths vary with their live values.
        if (btcStr != null) {
            val vp = valuePaint(Color.LTGRAY)
            repeat(2) {
                val yieldRightEdge = xYield + vp.measureText(yStr) / 2f
                val btcLeftEdge    = xBtc - vp.measureText(btcStr) / 2f
                val gap = btcLeftEdge - yieldRightEdge
                xBtc -= gap * 0.25f
            }
        }

        canvas.drawText("DXY", xDxy, 18f, labelPaint(Color.parseColor("#85BB65")))
        canvas.drawText("YIELD", xYield, 18f, labelPaint(Color.parseColor("#C8A84B")))
        canvas.drawText("BTC", xBtc, 18f, labelPaint(Color.parseColor("#FFD700")))

        if (dxy > 0f) canvas.drawText("%.2f".format(dxy), xDxy, 42f, valuePaint(Color.LTGRAY))
        canvas.drawText(yStr, xYield, 42f, valuePaint(sentColor))
        if (btcStr != null) canvas.drawText(btcStr, xBtc, 42f, valuePaint(Color.LTGRAY))

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(image = Icon.createWithBitmap(bitmap), type = SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("DXY, yield, BTC").build()
        ).setTapAction(refreshTapAction()).build()
    }

    private fun refreshTapAction(): PendingIntent =
        PendingIntent.getBroadcast(
            this, 0,
            Intent(this, RefreshRequestReceiver::class.java).setAction(RefreshRequestReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
