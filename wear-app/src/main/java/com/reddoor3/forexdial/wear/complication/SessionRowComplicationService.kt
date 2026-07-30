package com.reddoor3.forexdial.wear.complication

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.reddoor3.forexdial.wear.session.MarketSessionCalculator
import com.reddoor3.forexdial.wear.session.SessionStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// XETRA | LSE | NYSE in one bitmap/one slot. Session status is pure local
// calendar math (MarketSessionCalculator) - no phone data dependency, so this
// complication never returns null and needs no DataLayer refresh.
class SessionRowComplicationService : SuspendingComplicationDataSourceService() {

    private val sessFmt = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = build()

    private fun build(): ComplicationData {
        val bw = 432; val bh = 100
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tf      = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val tfLight = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

        val cOpen  = Color.parseColor("#00cc55")
        val cPre   = Color.parseColor("#ccaa00")
        val cClose = Color.parseColor("#cc1100")

        val now = ZonedDateTime.now()
        // Round 2, Ambar 2026-07-29: dials grown further using the vertical
        // space freed by moving the whole lower block up (36 -> 42 radius).
        // Round 1 had moved them in enough to overlap (fractions 0.35/0.65,
        // radius 36 -> centre-to-centre 64.8px < 2*36=72px). Widened slightly
        // for "a little breathing room" rather than reverting to the original
        // wide spread - net effect is still much closer to centre than the
        // very first pass (0.20/0.80). Text scaled with the radius.
        val circR = 42f; val circY = bh / 2f

        // Ambar 2026-07-29: could not tell which circle was which exchange -
        // the dials showed local time + OP/PR/CL status but NO exchange name
        // at all, so there was nothing to check the times against. Added a
        // small exchange label per circle. Three lines now (label/time/status)
        // means the time font shrinks slightly (27->24) to make room.
        // Ambar 2026-07-30: OP/PR/CL text removed - the dial's fill colour
        // already carries that information, the text was redundant. Freed
        // space used to double the exchange label (13->26) and grow the
        // time (24->32).
        // Ambar 2026-07-30: "XETRA" (5 letters) is wider than "LSE"/"NYSE" at
        // the same size, so it was poking slightly outside its circle. Sized
        // per-exchange rather than dropping all three - only XETRA needed to
        // shrink, LSE/NYSE stay at the full 26.
        listOf(
            Triple(ZoneId.of("Europe/Berlin"),    bw * 0.28f, MarketSessionCalculator.getXetraStatus()) to Pair("XETRA", 22f),
            Triple(ZoneId.of("Europe/London"),     bw * 0.5f, MarketSessionCalculator.getLseStatus()) to Pair("LSE", 26f),
            Triple(ZoneId.of("America/New_York"), bw * 0.72f, MarketSessionCalculator.getNyseStatus()) to Pair("NYSE", 26f)
        ).forEach { (triple, exchangeAndSize) ->
            val (zone, x, status) = triple
            val (exchange, labelSize) = exchangeAndSize
            val bg = when (status) {
                SessionStatus.OPEN      -> cOpen
                SessionStatus.PRE_POST  -> cPre
                SessionStatus.CLOSED    -> cClose
            }
            canvas.drawCircle(x, circY, circR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

            canvas.drawText(exchange, x, circY - 12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tfLight; textSize = labelSize; color = Color.WHITE; textAlign = Paint.Align.CENTER
                })
            val local = now.withZoneSameInstant(zone)
            canvas.drawText(local.format(sessFmt), x, circY + 20f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = 32f; color = Color.WHITE; textAlign = Paint.Align.CENTER
                })
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(image = Icon.createWithBitmap(bitmap), type = SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("XETRA, LSE, NYSE session status").build()
        ).build()
    }
}
