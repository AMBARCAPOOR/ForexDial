package com.reddoor3.forexdial.wear.session

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SessionStatus(val value: Int) {
    CLOSED(0), PRE_POST(1), OPEN(2)
}

object MarketSessionCalculator {
    private val NY     = ZoneId.of("America/New_York")
    private val BERLIN = ZoneId.of("Europe/Berlin")
    private val LONDON = ZoneId.of("Europe/London")

    fun getNyseStatus():  SessionStatus = calcStatus(ZonedDateTime.now(NY),     LocalTime.of(9, 30), LocalTime.of(16, 0))
    fun getXetraStatus(): SessionStatus = calcStatus(ZonedDateTime.now(BERLIN), LocalTime.of(9, 0),  LocalTime.of(17, 30))
    fun getLseStatus():   SessionStatus = calcStatus(ZonedDateTime.now(LONDON), LocalTime.of(8, 0),  LocalTime.of(16, 30))

    private fun calcStatus(now: ZonedDateTime, open: LocalTime, close: LocalTime): SessionStatus {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return SessionStatus.CLOSED
        val t = now.toLocalTime()
        return when {
            t >= open && t < close                                           -> SessionStatus.OPEN
            (t >= open.minusMinutes(30) && t < open)
                || (t >= close && t < close.plusMinutes(30))                 -> SessionStatus.PRE_POST
            else                                                             -> SessionStatus.CLOSED
        }
    }
}
