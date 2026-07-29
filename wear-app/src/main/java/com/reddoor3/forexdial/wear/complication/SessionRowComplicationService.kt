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
        val bw = 432; val bh = 70
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tf      = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val tfLight = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

        val cOpen  = Color.parseColor("#00cc55")
        val cPre   = Color.parseColor("#ccaa00")
        val cClose = Color.parseColor("#cc1100")

        val now = ZonedDateTime.now()
        // Radius/spread kept modest - this row sits low on a circular face and
        // the outer edges of wide circles clip against the bezel (the same
        // mistake as the first PartDraw test earlier this session).
        val circR = 28f; val circY = bh / 2f

        listOf(
            Triple(ZoneId.of("Europe/Berlin"),    bw * 0.20f, MarketSessionCalculator.getXetraStatus()),
            Triple(ZoneId.of("Europe/London"),     bw * 0.5f, MarketSessionCalculator.getLseStatus()),
            Triple(ZoneId.of("America/New_York"), bw * 0.80f, MarketSessionCalculator.getNyseStatus())
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
                    typeface = tf; textSize = 18f; color = Color.WHITE; textAlign = Paint.Align.CENTER
                })
            val label = when (status) { SessionStatus.OPEN -> "OP"; SessionStatus.PRE_POST -> "PR"; SessionStatus.CLOSED -> "CL" }
            canvas.drawText(label, x, circY + 16f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tfLight; textSize = 13f; color = Color.WHITE; alpha = 220; textAlign = Paint.Align.CENTER
                })
        }

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(image = Icon.createWithBitmap(bitmap), type = SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("XETRA, LSE, NYSE session status").build()
        ).build()
    }
}
