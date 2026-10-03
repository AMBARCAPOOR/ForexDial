package com.reddoor3.forexdial.wear.session

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SessionStatus(val value: Int) {
    CLOSED(0), PRE_POST(1), OPEN(2)
}

// FOREX sessions, not stock-exchange hours (Ambar 2026-10-02: "the market
// times follow the traditional stock exchange times but they should follow
// the FOREX market times instead").
//
// He was right, and the difference is bigger than a few minutes either side:
//
//   session      FX hours (local)    the exchange hours this replaced
//   Tokyo        09:00-18:00 JST     TSE  09:00-15:30, plus a lunch recess
//   London       08:00-17:00         LSE  08:00-16:30
//   New York     08:00-17:00 ET      NYSE 09:30-16:00
//
// Sessions are defined in each city's OWN local time rather than converted to
// a single zone. They're local business hours, so each follows its own
// country's DST - London and New York shift on different dates, and Japan
// doesn't shift at all. Expressing them locally gets that right for free;
// hard-coding UTC or ET offsets would drift for weeks each spring and autumn.
//
// Verified 2026-10-02 these match the usual UTC windows: Tokyo 00:00-09:00,
// London 07:00-16:00, New York 12:00-21:00 (summer).
object MarketSessionCalculator {

    private val TOKYO  = ZoneId.of("Asia/Tokyo")
    private val LONDON = ZoneId.of("Europe/London")
    private val NY     = ZoneId.of("America/New_York")

    // The 24/5 window. FX opens Sunday 17:00 New York time and closes Friday
    // 17:00 New York time - anchored to New York, so it moves with US DST the
    // way the market actually does.
    //
    // This gate is why the old "Saturday or Sunday means closed" test had to
    // go: it is WRONG for Sunday evening, when the week has already begun and
    // Tokyo is trading. Getting that wrong would have shown a dead face during
    // the Sunday open - exactly when a trader is looking for the week's first
    // move.
    private val WEEK_OPEN  = LocalTime.of(17, 0)   // Sunday, NY time
    private val WEEK_CLOSE = LocalTime.of(17, 0)   // Friday, NY time

    private fun isMarketOpen(nowNy: ZonedDateTime): Boolean {
        val t = nowNy.toLocalTime()
        return when (nowNy.dayOfWeek) {
            DayOfWeek.SATURDAY -> false
            DayOfWeek.SUNDAY   -> t >= WEEK_OPEN      // opens Sunday evening
            DayOfWeek.FRIDAY   -> t < WEEK_CLOSE      // shuts Friday evening
            else               -> true
        }
    }

    fun getTokyoStatus():  SessionStatus = status(TOKYO,  LocalTime.of(9, 0), LocalTime.of(18, 0))
    fun getLondonStatus(): SessionStatus = status(LONDON, LocalTime.of(8, 0), LocalTime.of(17, 0))
    fun getNyStatus():     SessionStatus = status(NY,     LocalTime.of(8, 0), LocalTime.of(17, 0))

    private fun status(zone: ZoneId, open: LocalTime, close: LocalTime): SessionStatus {
        // One instant, read in two zones: the session's own for its hours, New
        // York's for the weekly gate.
        val now = ZonedDateTime.now(zone)
        if (!isMarketOpen(now.withZoneSameInstant(NY))) return SessionStatus.CLOSED

        val t = now.toLocalTime()
        val openAt  = now.toLocalDate().atTime(open).atZone(zone)
        val closeAt = now.toLocalDate().atTime(close).atZone(zone)

        // The 30-minute pre/post windows are gated on whether the session they
        // belong to actually trades, not just on the clock.
        //
        // Without that, Sunday 17:00 NY - the moment the FX week OPENS - showed
        // New York as post-close, because 17:00 is also its closing time and the
        // weekly gate had just gone open. There was no Sunday New York session to
        // be post-close of. Caught by the weekend test below.
        return when {
            t >= open && t < close                      -> SessionStatus.OPEN
            t >= open.minusMinutes(30) && t < open &&
                trades(openAt)                          -> SessionStatus.PRE_POST
            t >= close && t < close.plusMinutes(30) &&
                trades(closeAt.minusMinutes(1))         -> SessionStatus.PRE_POST
            else                                        -> SessionStatus.CLOSED
        }
    }

    // Was the FX market open at this instant? Used to ask whether a session
    // really ran, in the session's own terms, rather than special-casing days.
    private fun trades(at: ZonedDateTime): Boolean =
        isMarketOpen(at.withZoneSameInstant(NY))
}
