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
    private val TOKYO  = ZoneId.of("Asia/Tokyo")
    private val LONDON = ZoneId.of("Europe/London")

    fun getNyseStatus(): SessionStatus = calcStatus(ZonedDateTime.now(NY),     LocalTime.of(9, 30), LocalTime.of(16, 0))
    fun getLseStatus():  SessionStatus = calcStatus(ZonedDateTime.now(LONDON), LocalTime.of(8, 0),  LocalTime.of(16, 30))

    // Tokyo replaced XETRA 2026-09-07 (Ambar). Two things make it unlike the
    // other two:
    //
    // 1. It breaks for lunch, 11:30-12:30 JST - no other dial on this face
    //    does. Rendering that as CLOSED would flash the dial red mid-session
    //    and read as a bug, so the recess is PRE_POST instead: amber already
    //    means "not in full continuous session" here, which is exactly what a
    //    midday recess is. Change LUNCH_STATUS below to OPEN to ignore the
    //    break entirely (closer to how the FX Tokyo session behaves, since
    //    currency trading doesn't pause for the equity recess), or to CLOSED
    //    for literal exchange accuracy.
    //
    // 2. Japan does not observe DST, so JST is UTC+9 year-round - one less
    //    thing to go wrong than Berlin/London/New York.
    //
    // Hours verified 2026-09-07 against JPX: 09:00-11:30 and 12:30-15:30 JST.
    // The 15:30 close is the extension from the previous 15:00 - do not
    // "correct" it back.
    private val TOKYO_OPEN        = LocalTime.of(9, 0)
    private val TOKYO_LUNCH_START = LocalTime.of(11, 30)
    private val TOKYO_LUNCH_END   = LocalTime.of(12, 30)
    private val TOKYO_CLOSE       = LocalTime.of(15, 30)
    private val LUNCH_STATUS      = SessionStatus.PRE_POST

    fun getTokyoStatus(): SessionStatus {
        val now = ZonedDateTime.now(TOKYO)
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) {
            return SessionStatus.CLOSED
        }
        val t = now.toLocalTime()
        return when {
            t >= TOKYO_LUNCH_START && t < TOKYO_LUNCH_END -> LUNCH_STATUS
            t >= TOKYO_OPEN && t < TOKYO_CLOSE            -> SessionStatus.OPEN
            t >= TOKYO_OPEN.minusMinutes(30) && t < TOKYO_OPEN   -> SessionStatus.PRE_POST
            t >= TOKYO_CLOSE && t < TOKYO_CLOSE.plusMinutes(30)  -> SessionStatus.PRE_POST
            else -> SessionStatus.CLOSED
        }
    }

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
