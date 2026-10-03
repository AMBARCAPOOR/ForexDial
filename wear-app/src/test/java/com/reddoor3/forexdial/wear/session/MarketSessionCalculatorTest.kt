package com.reddoor3.forexdial.wear.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * FX session hours. These cases exist because none of them can be observed on
 * demand: the weekend boundaries come round once a week, and the DST mismatch
 * between New York and London lasts a fortnight a year.
 *
 * Times are written in New York local time, since that is what the weekly gate
 * is anchored to.
 */
class MarketSessionCalculatorTest {

    private val ny = ZoneId.of("America/New_York")

    private fun at(nyLocal: String): Instant =
        LocalDateTime.parse(nyLocal).atZone(ny).toInstant()

    private fun assertSessions(
        nyLocal: String,
        tokyo: SessionStatus,
        london: SessionStatus,
        newYork: SessionStatus,
    ) {
        val i = at(nyLocal)
        assertEquals("Tokyo at $nyLocal NY",    tokyo,   MarketSessionCalculator.getTokyoStatus(i))
        assertEquals("London at $nyLocal NY",   london,  MarketSessionCalculator.getLondonStatus(i))
        assertEquals("New York at $nyLocal NY", newYork, MarketSessionCalculator.getNyStatus(i))
    }

    private val OPEN = SessionStatus.OPEN
    private val SHUT = SessionStatus.CLOSED
    private val EDGE = SessionStatus.PRE_POST

    // ── The weekly 24/5 window ───────────────────────────────────────────────

    @Test fun `new york trades up to the final minute of the week`() {
        assertSessions("2026-10-02T16:59", SHUT, SHUT, OPEN)
    }

    @Test fun `week closes friday 1700 new york`() {
        assertSessions("2026-10-02T17:00", SHUT, SHUT, SHUT)
    }

    /**
     * New York's post-close window would otherwise run to 17:30, but the week
     * has ended - there is nothing left open to be winding down from.
     */
    @Test fun `no post-close window once the week has shut`() {
        assertSessions("2026-10-02T17:10", SHUT, SHUT, SHUT)
    }

    /** Friday 19:00 NY is inside Tokyo's local hours, but the week is over. */
    @Test fun `tokyo hours on friday night do not reopen the week`() {
        assertSessions("2026-10-02T19:00", SHUT, SHUT, SHUT)
    }

    @Test fun `saturday is dead`() {
        assertSessions("2026-10-03T12:00", SHUT, SHUT, SHUT)
    }

    @Test fun `sunday afternoon is still shut`() {
        assertSessions("2026-10-04T16:59", SHUT, SHUT, SHUT)
    }

    /**
     * Regression: 17:00 is also New York's CLOSING time, so the moment the week
     * opened the face rendered New York as post-close - a session that had
     * never run that day. Pre/post windows are now gated on the session either
     * side of them actually trading.
     */
    @Test fun `week opening sunday 1700 does not fake a new york post-close`() {
        assertSessions("2026-10-04T17:00", SHUT, SHUT, SHUT)
        assertSessions("2026-10-04T17:20", SHUT, SHUT, SHUT)
    }

    /** The old "Saturday or Sunday means closed" rule got this one wrong. */
    @Test fun `tokyo opens the week on sunday evening new york time`() {
        assertSessions("2026-10-04T19:30", EDGE, SHUT, SHUT)   // pre-open
        assertSessions("2026-10-04T20:00", OPEN, SHUT, SHUT)   // open
    }

    // ── Sessions within the week ─────────────────────────────────────────────

    @Test fun `london opens while tokyo is still trading`() {
        assertSessions("2026-10-05T02:40", OPEN, EDGE, SHUT)
        assertSessions("2026-10-05T03:00", OPEN, OPEN, SHUT)
    }

    @Test fun `tokyo winds down into the london morning`() {
        assertSessions("2026-10-05T05:10", EDGE, OPEN, SHUT)
    }

    @Test fun `london and new york overlap`() {
        assertSessions("2026-10-05T07:40", SHUT, OPEN, EDGE)
        assertSessions("2026-10-05T09:00", SHUT, OPEN, OPEN)
        assertSessions("2026-10-05T12:10", SHUT, EDGE, OPEN)
    }

    @Test fun `new york trades on alone into the evening`() {
        assertSessions("2026-10-05T13:00", SHUT, SHUT, OPEN)
    }

    /** Same clock time as the Friday case, but mid-week the window is real. */
    @Test fun `new york post-close is shown on a weekday`() {
        assertSessions("2026-10-05T17:10", SHUT, SHUT, EDGE)
    }

    // ── Daylight saving ──────────────────────────────────────────────────────

    /**
     * New York moved to DST on 8 March 2026; the UK does not until 29 March.
     * For that fortnight the two are 4 hours apart rather than the usual 5, so
     * London's open lands an hour LATER in New York terms - 04:00 instead of
     * 03:00. Hard-coding a UTC or ET offset would be wrong for the whole
     * period; defining each session in its own city's time is what gets it
     * right.
     */
    @Test fun `london opens an hour later in new york terms during the dst gap`() {
        // Before the gap, NY is 5 hours behind: 03:00 NY is 08:00 London.
        assertSessions("2026-03-03T03:00", OPEN, OPEN, SHUT)
        // Inside it, NY is only 4 behind: 03:00 NY is 07:00 London, too early.
        assertSessions("2026-03-10T03:00", OPEN, SHUT, SHUT)
        // An hour on, London is open again.
        assertSessions("2026-03-10T04:00", OPEN, OPEN, SHUT)
    }

    /** Japan observes no DST at all, so Tokyo moves when the others do. */
    @Test fun `tokyo session is stable across the year in its own time`() {
        for (date in listOf("2026-01-14", "2026-07-15", "2026-11-04")) {
            val i = at("${date}T20:30")
            assertEquals("Tokyo 09:30 JST on $date", OPEN, MarketSessionCalculator.getTokyoStatus(i))
        }
    }

    // ── A property the face depends on ───────────────────────────────────────

    /**
     * Tokyo and New York never overlap, so all three dials can never be green.
     * The README used to show a screenshot claiming they could.
     */
    @Test fun `all three sessions are never open at once`() {
        var start = at("2026-07-05T00:00")
        repeat(7 * 24 * 60) { minute ->
            val i = start.plusSeconds(minute * 60L)
            val allOpen = MarketSessionCalculator.getTokyoStatus(i) == OPEN &&
                MarketSessionCalculator.getLondonStatus(i) == OPEN &&
                MarketSessionCalculator.getNyStatus(i) == OPEN
            if (allOpen) throw AssertionError("all three open at $i")
        }
    }
}
