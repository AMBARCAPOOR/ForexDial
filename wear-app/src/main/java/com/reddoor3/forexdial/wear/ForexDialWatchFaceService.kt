package com.reddoor3.forexdial.wear

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

class ForexDialWatchFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace = WatchFace(
        WatchFaceType.DIGITAL,
        ForexDialRenderer(surfaceHolder, currentUserStyleRepository, watchState, applicationContext)
    )
}

private class ForexDialRenderer(
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    watchState: WatchState,
    private val ctx: Context
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder, currentUserStyleRepository, watchState,
    CanvasType.SOFTWARE, 1000L, true
) {
    private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val secFmt  = DateTimeFormatter.ofPattern(":ss")
    private val sessFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val tf       = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tfLight  = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    // Locked color system
    private val C_CYAN    = 0xFF00e5ff.toInt()
    private val C_ORANGE  = 0xFFFF7700.toInt()
    private val C_AMBER   = 0xFFFFB800.toInt()
    private val C_GREEN   = 0xFF85BB65.toInt()   // DXY label — dollar green
    private val C_MUSTARD = 0xFFC8A84B.toInt()   // YIELD label
    private val C_GOLD    = 0xFFFFD700.toInt()   // BTC label
    private val C_GRAY    = 0xFF888888.toInt()
    private val C_S_OPEN  = 0xFF00cc55.toInt()
    private val C_S_PRE   = 0xFFccaa00.toInt()
    private val C_S_CLOSE = 0xFFcc1100.toInt()

    override suspend fun createSharedAssets(): Renderer.SharedAssets = object : Renderer.SharedAssets {
        override fun onDestroy() {}
    }

    override fun renderHighlightLayer(
        canvas: Canvas, bounds: Rect, z: ZonedDateTime, a: Renderer.SharedAssets
    ) = canvas.drawColor(renderParameters.highlightLayer?.backgroundTint ?: Color.TRANSPARENT)

    override fun render(
        canvas: Canvas, bounds: Rect, now: ZonedDateTime, sharedAssets: Renderer.SharedAssets
    ) {
        val cx      = bounds.exactCenterX()          // 216 on GW7
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT

        canvas.drawColor(Color.BLACK)

        // DEBUG — shows actual canvas dimensions; remove after confirming
        canvas.drawText("${bounds.width()}x${bounds.height()}",
            bounds.exactCenterX(), bounds.exactCenterY(),
            paint(22f, Color.RED, tf).also { it.textAlign = Paint.Align.CENTER })

        // Read shared prefs
        val prefs     = ctx.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val price     = prefs.getFloat(WatchConstants.KEY_EURUSD, 0f)
        val prev      = prefs.getFloat(WatchConstants.KEY_EURUSD_PREV, 0f)
        val dxy       = prefs.getFloat(WatchConstants.KEY_DXY, 0f)
        val btc       = prefs.getFloat(WatchConstants.KEY_BTC, 0f)
        val sentiment = prefs.getString(WatchConstants.KEY_SENTIMENT, "NEUT") ?: "NEUT"
        val yieldVal  = prefs.getFloat(WatchConstants.KEY_YIELD_SPREAD, Float.MAX_VALUE)

        val sentColor = when (sentiment) {
            "LONG"  -> C_CYAN
            "SHORT" -> C_ORANGE
            else    -> Color.GRAY
        }

        if (!ambient) {

            // ── TOP ROW: DXY | YIELD | BTC ───────────────────────────────────
            val xDxy   = cx - 110f
            val xYield = cx
            val xBtc   = cx + 110f

            paint(12f, C_GREEN,   tfLight).let { canvas.drawTextC("DXY",   xDxy,   66f, it) }
            paint(12f, C_MUSTARD, tfLight).let { canvas.drawTextC("YIELD", xYield, 66f, it) }
            paint(12f, C_GOLD,    tfLight).let { canvas.drawTextC("BTC",   xBtc,   66f, it) }

            if (dxy > 0f)
                canvas.drawTextC("%.2f".format(dxy), xDxy, 88f, paint(20f, Color.LTGRAY, tf))

            val yStr = if (yieldVal != Float.MAX_VALUE && abs(yieldVal) < 20f)
                (if (yieldVal >= 0f) "+" else "") + "%.2f".format(yieldVal)
            else sentiment
            canvas.drawTextC(yStr, xYield, 88f, paint(20f, sentColor, tf))

            if (btc > 0f) {
                val btcStr = if (btc >= 1_000f) "${"%.0f".format(btc / 1_000f)}k"
                             else               "%.0f".format(btc)
                canvas.drawTextC(btcStr, xBtc, 88f, paint(20f, Color.LTGRAY, tf))
            }

            // ── EUR/USD BOUNDING BOX + CONTENT ───────────────────────────────
            val boxL = 72f; val boxR = 360f
            val boxT = 100f; val boxB = 196f

            // Box border = YIELD signal color
            canvas.drawRect(boxL, boxT, boxR, boxB,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style       = Paint.Style.STROKE
                    strokeWidth = 2f
                    color       = sentColor
                })

            // EUR/USD label
            canvas.drawTextC("EUR / USD", cx, 115f, paint(12f, C_AMBER, tfLight))

            if (price > 0f) {
                val rising   = price >= prev
                val pipColor = if (rising) C_CYAN else C_ORANGE
                val pStr     = "%.5f".format(price)
                val main     = pStr.dropLast(2)   // "1.140"
                val pip      = pStr.takeLast(2)   // "74"

                val mainP  = paint(58f, Color.WHITE, tf)
                val pipP   = paint(66f, pipColor,   tf)
                val rateW  = mainP.measureText(main) + pipP.measureText(pip)
                val rateX  = cx - rateW / 2f

                canvas.drawText(main, rateX, 163f, mainP)
                canvas.drawText(pip, rateX + mainP.measureText(main), 168f, pipP)

                // Pips bar
                if (prev > 0f) {
                    val diff  = (price - prev).toDouble()
                    val pips  = (diff * 10_000).roundToLong()
                    val pct   = diff / prev * 100.0
                    val arrow = if (diff >= 0) "▲" else "▼"
                    val bar   = "$arrow ${if (pips >= 0) "+" else ""}${pips} pips  ${if (pct >= 0) "+" else ""}${"%.2f".format(pct)}%"
                    canvas.drawTextC(bar, cx, 187f, paint(12f, pipColor, tfLight))
                }
            }

            // ── BATTERY CANDLE ────────────────────────────────────────────────
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batt = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .toFloat().coerceIn(0f, 100f)
            drawBatteryCandle(canvas, cx = boxL - 16f, cy = 248f, battPct = batt)
        }

        // ── TIME: HH:mm (large) + :ss ─────────────────────────────────────────
        val tPaint  = paint(72f, Color.WHITE, tf)
        val tStr    = now.format(timeFmt)
        canvas.drawTextC(tStr, cx, 258f, tPaint)
        if (!ambient) {
            val secX = cx + tPaint.measureText(tStr) / 2f + 2f
            canvas.drawText(now.format(secFmt), secX, 244f, paint(28f, C_GRAY, tf))
        }

        // ── DATE (below time) ─────────────────────────────────────────────────
        canvas.drawTextC(
            now.format(dateFmt).uppercase(), cx, 278f,
            paint(14f, if (ambient) Color.DKGRAY else C_GRAY, tfLight)
        )

        // ── SESSION CIRCLES: XETRA · LSE · NYSE ──────────────────────────────
        val circY = 352f
        val circR = 40f
        listOf(
            Triple(ZoneId.of("Europe/Berlin"),    cx - 116f, "XETRA"),
            Triple(ZoneId.of("Europe/London"),    cx,         "LSE"),
            Triple(ZoneId.of("America/New_York"), cx + 116f, "NYSE")
        ).forEach { (zone, x, _) ->
            val local  = now.withZoneSameInstant(zone)
            val status = sessionStatus(local)
            val bg     = if (ambient) Color.DKGRAY else when (status) {
                "OP" -> C_S_OPEN
                "PR" -> C_S_PRE
                else -> C_S_CLOSE
            }
            canvas.drawCircle(x, circY, circR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
            // Time inside circle
            canvas.drawText(local.format(sessFmt), x, circY - 2f,
                paint(16f, Color.WHITE, tf).also { it.textAlign = Paint.Align.CENTER })
            // Status label
            canvas.drawText(status, x, circY + 16f,
                paint(13f, Color.WHITE, tfLight).also {
                    it.textAlign = Paint.Align.CENTER
                    it.alpha = 220
                })
        }
    }

    // Candlestick-style battery with proportional fill from bottom up
    private fun drawBatteryCandle(canvas: Canvas, cx: Float, cy: Float, battPct: Float) {
        val bodyW  = 20f
        val bodyH  = 80f
        val wickW  = 10f   // 5× 2px outline stroke
        val wickH  = 18f
        val left   = cx - bodyW / 2f
        val right  = cx + bodyW / 2f
        val top    = cy - bodyH / 2f
        val bot    = cy + bodyH / 2f

        val (fillC, darkC) = when {
            battPct > 50f -> Pair(0xFF00C8DA.toInt(), 0xFF007888.toInt())  // cyan
            battPct > 20f -> Pair(0xFFFF9520.toInt(), 0xFFCC5800.toInt())  // amber
            else          -> Pair(0xFFFF4444.toInt(), 0xFFCC1010.toInt())  // red
        }

        val dk  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkC }
        val fl  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillC }
        val blk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        // Wicks
        canvas.drawRect(cx - wickW / 2f, top - wickH, cx + wickW / 2f, top, dk)
        canvas.drawRect(cx - wickW / 2f, bot,          cx + wickW / 2f, bot + wickH, dk)
        // Body outline (dark solid rect)
        canvas.drawRect(left, top, right, bot, dk)
        // Body interior (black = empty)
        canvas.drawRect(left + 2f, top + 2f, right - 2f, bot - 2f, blk)
        // Fill from bottom up proportional to battery %
        val fillH = (bodyH - 4f) * (battPct / 100f)
        if (fillH > 0f)
            canvas.drawRect(left + 2f, bot - 2f - fillH, right - 2f, bot - 2f, fl)
    }

    private fun sessionStatus(now: ZonedDateTime): String {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return "CL"
        val m = now.hour * 60 + now.minute
        return when (now.zone.id) {
            "America/New_York" -> when {
                m in 570..959   -> "OP"; m in 510..569  -> "PR"; m in 960..1019  -> "PR"; else -> "CL"
            }
            "Europe/Berlin" -> when {
                m in 540..1049  -> "OP"; m in 480..539  -> "PR"; m in 1050..1109 -> "PR"; else -> "CL"
            }
            else -> when {   // Europe/London
                m in 480..989   -> "OP"; m in 420..479  -> "PR"; m in 990..1049  -> "PR"; else -> "CL"
            }
        }
    }

    private fun paint(size: Float, color: Int, typeface: Typeface) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface  = typeface
            this.textSize  = size
            this.color     = color
            this.textAlign = Paint.Align.LEFT
        }

    private fun Canvas.drawTextC(text: String, cx: Float, y: Float, p: Paint) {
        val s = p.textAlign; p.textAlign = Paint.Align.CENTER
        drawText(text, cx, y, p); p.textAlign = s
    }
}
