package com.reddoor3.forexdial.wear.complication

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.DataLayerHelper
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
        build(101.14f, -1.49f, "SHORT", 67420f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        // ALWAYS refresh - see EurUsdComplicationService for why gating on
        // ==0f was the actual bug (stale-but-present values never refreshed).
        DataLayerHelper.refreshFromDataLayer(this)
        val dxy   = prefs.getFloat(WatchConstants.KEY_DXY, 0f)
        val yield = prefs.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE)
        val sent  = prefs.getString(WatchConstants.KEY_SENTIMENT, "NEUT") ?: "NEUT"
        val btc   = prefs.getFloat(WatchConstants.KEY_BTC, 0f)
        if (dxy == 0f && btc == 0f) return null
        return build(dxy, if (yield == Float.MAX_VALUE) null else yield, sent, btc)
    }

    private fun build(dxy: Float, yield: Float?, sentiment: String, btc: Float): ComplicationData {
        val bw = 432; val bh = 60
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tf      = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val tfLight = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

        val sentColor = when (sentiment) {
            "LONG"  -> Color.parseColor("#00e5ff")
            "SHORT" -> Color.parseColor("#FF7700")
            else    -> Color.LTGRAY
        }

        // Sizes set by Ambar 2026-07-29: labels +50% (14->21), values +20% (22->26).
        fun labelPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfLight; textSize = 21f; this.color = color; textAlign = Paint.Align.CENTER
        }
        fun valuePaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = 26f; this.color = color; textAlign = Paint.Align.CENTER
        }

        // Moved in toward centre by 1/4 of the prior offset from 0.5
        // (0.28 -> 0.21), per Ambar 2026-07-29.
        val xDxy = bw * 0.29f; val xYield = bw * 0.5f; val xBtc = bw * 0.71f

        canvas.drawText("DXY", xDxy, 18f, labelPaint(Color.parseColor("#85BB65")))
        canvas.drawText("YIELD", xYield, 18f, labelPaint(Color.parseColor("#C8A84B")))
        canvas.drawText("BTC", xBtc, 18f, labelPaint(Color.parseColor("#FFD700")))

        if (dxy > 0f) canvas.drawText("%.2f".format(dxy), xDxy, 42f, valuePaint(Color.LTGRAY))
        val yStr = yield?.let { (if (it >= 0f) "+" else "") + "%.2f".format(it) } ?: sentiment
        canvas.drawText(yStr, xYield, 42f, valuePaint(sentColor))
        if (btc > 0f) {
            val btcStr = if (btc >= 1_000f) "${"%.0f".format(btc / 1_000f)}k" else "%.0f".format(btc)
            canvas.drawText(btcStr, xBtc, 42f, valuePaint(Color.LTGRAY))
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(image = Icon.createWithBitmap(bitmap), type = SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("DXY, yield, BTC").build()
        ).build()
    }
}
