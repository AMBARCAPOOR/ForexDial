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

        listOf(
            Triple(ZoneId.of("Europe/Berlin"),    bw * 0.28f, MarketSessionCalculator.getXetraStatus()),
            Triple(ZoneId.of("Europe/London"),     bw * 0.5f, MarketSessionCalculator.getLseStatus()),
            Triple(ZoneId.of("America/New_York"), bw * 0.72f, MarketSessionCalculator.getNyseStatus())
        ).forEach { (zone, x, status) ->
            val bg = when (status) {
                SessionStatus.OPEN      -> cOpen
                SessionStatus.PRE_POST  -> cPre
                SessionStatus.CLOSED    -> cClose
            }
            canvas.drawCircle(x, circY, circR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

            val local = now.withZoneSameInstant(zone)
            canvas.drawText(local.format(sessFmt), x, circY - 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = 27f; color = Color.WHITE; textAlign = Paint.Align.CENTER
                })
            val label = when (status) { SessionStatus.OPEN -> "OP"; SessionStatus.PRE_POST -> "PR"; SessionStatus.CLOSED -> "CL" }
            canvas.drawText(label, x, circY + 23f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tfLight; textSize = 20f; color = Color.WHITE; alpha = 220; textAlign = Paint.Align.CENTER
                })
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(image = Icon.createWithBitmap(bitmap), type = SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("XETRA, LSE, NYSE session status").build()
        ).build()
    }
}
