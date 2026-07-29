package com.reddoor3.forexdial.wear.complication

import android.graphics.*
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NyseSessionComplicationService : SuspendingComplicationDataSourceService() {

    private val tz  = ZoneId.of("America/New_York")
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SMALL_IMAGE -> buildImage("09:30", "OP")
            else -> buildShortText("09:30", "OP")
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val now  = ZonedDateTime.now(tz)
        val time = now.format(fmt)
        val st   = status(now)
        return when (request.complicationType) {
            ComplicationType.SMALL_IMAGE -> buildImage(time, st)
            else -> buildShortText(time, st)
        }
    }

    private fun status(now: ZonedDateTime): String {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return "CL"
        val m = now.hour * 60 + now.minute
        return when {
            m in 570..959   -> "OP"   // 09:30–15:59 ET open
            m in 510..569   -> "PR"   // 08:30–09:29 pre-open (1hr)
            m in 960..1019  -> "PR"   // 16:00–16:59 post-close (1hr)
            else            -> "CL"
        }
    }

    private fun buildImage(time: String, status: String): ComplicationData {
        val bgColor = when (status) {
            "OP" -> Color.parseColor("#00cc55")
            "PR" -> Color.parseColor("#ccaa00")
            else -> Color.parseColor("#cc1100")
        }
        val sz = 200
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp)
        val r   = sz / 2f

        c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })

        val tf = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val p  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface  = tf
            textSize  = 58f
            color     = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val y = r - (p.descent() + p.ascent()) / 2f
        c.drawText(time, r, y, p)

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(Icon.createWithBitmap(bmp), SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("NYSE $time").build()
        ).build()
    }

    private fun buildShortText(time: String, status: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(time).build(),
            contentDescription = PlainComplicationText.Builder("NYSE $status").build()
        ).setTitle(PlainComplicationText.Builder(status).build()).build()
}
