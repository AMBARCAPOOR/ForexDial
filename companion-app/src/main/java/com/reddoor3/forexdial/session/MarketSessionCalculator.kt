package com.reddoor3.forexdial.session

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SessionStatus(val value: Int) {
    CLOSED(0),    // red   #E53935
    PRE_POST(1),  // amber #FFB300  (30 min before open / after close)
    OPEN(2)       // green #00C87A
}

object MarketSessionCalculator {

    private val NY     = ZoneId.of("America/New_York")
    private val BERLIN = ZoneId.of("Europe/Berlin")
    private val LONDON = ZoneId.of("Europe/London")

    private val NYSE_OPEN  = LocalTime.of(9, 30)
    private val NYSE_CLOSE = LocalTime.of(16, 0)

    private val XETRA_OPEN  = LocalTime.of(9, 0)
    private val XETRA_CLOSE = LocalTime.of(17, 30)

    private val LSE_OPEN  = LocalTime.of(8, 0)
    private val LSE_CLOSE = LocalTime.of(16, 30)

    fun getNyseStatus(now: ZonedDateTime = ZonedDateTime.now(NY)): SessionStatus =
        calcStatus(now, NYSE_OPEN, NYSE_CLOSE)

    fun getXetraStatus(now: ZonedDateTime = ZonedDateTime.now(BERLIN)): SessionStatus =
        calcStatus(now, XETRA_OPEN, XETRA_CLOSE)

    fun getLseStatus(now: ZonedDateTime = ZonedDateTime.now(LONDON)): SessionStatus =
        calcStatus(now, LSE_OPEN, LSE_CLOSE)

    private fun calcStatus(now: ZonedDateTime, open: LocalTime, close: LocalTime): SessionStatus {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) {
            return SessionStatus.CLOSED
        }
        val t = now.toLocalTime()
        val preOpen   = open.minusMinutes(30)
        val postClose = close.plusMinutes(30)
        return when {
            t >= open && t < close                     -> SessionStatus.OPEN
            (t >= preOpen && t < open)
                || (t >= close && t < postClose)       -> SessionStatus.PRE_POST
            else                                       -> SessionStatus.CLOSED
        }
    }
}
