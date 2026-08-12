package com.reddoor3.forexdial.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

// Buzzes the watch once per newly-fired price alert.
//
// FIXED 2026-08-09 (Ambar: "the watch doesn't vibrate when the alert fires").
// The buzz used to live only in ForexDataListenerService.onDataChanged - the
// DataItem PUSH path. That path is unreliable on this device, which is the
// whole reason DataLayerHelper.refreshFromDataLayer exists and is called by
// every complication on every request. So the alert ICON appeared (complication
// polling, reliable) while the buzz silently never ran (push, unreliable).
//
// Now driven from the polling path as well, with a stored "already buzzed for
// this ts" marker so that either path can trigger it and one crossing still
// buzzes exactly once.
object AlertBuzzer {

    private const val TAG = "FDD_AlertBuzzer"

    // Ambar 2026-08-12: "can it keep buzzing until deactivated?" - yes. It now
    // re-buzzes until the alert is dismissed, instead of once per crossing.
    //
    // 45s rather than 60s deliberately: the alert complication polls on a 60s
    // period, and a 60s gate would sometimes be evaluated a fraction early and
    // skip that round, giving a ragged 2-minute rhythm. 45s means every poll
    // qualifies, so the real cadence is the poll's own ~60s.
    private const val REPEAT_INTERVAL_MS = 45_000L

    // Safety cap, ~20 minutes of nagging. Without it an alert that fires while
    // the watch is on a charger overnight would buzz until the battery died.
    // Raise or drop this freely - it's the one number here that's a judgement
    // call rather than a constraint.
    private const val MAX_BUZZES = 20

    // Complications call refreshFromDataLayer concurrently, so two threads can
    // reach the check at once. Synchronized, and the marker is committed
    // BEFORE vibrating, so the second caller sees the claim and backs off.
    @Synchronized
    fun buzzIfNewAlert(context: Context) {
        val prefs = context.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val alertTs = prefs.getLong(WatchConstants.KEY_ALERT_TS, 0L)

        if (alertTs == 0L) return
        // Cleared by the user - stop nagging immediately.
        if (alertTs == prefs.getLong(WatchConstants.KEY_ALERT_DISMISSED_TS, 0L)) return

        // Counters belong to a specific alert; a newer crossing resets them so
        // it gets its own full run of reminders.
        val sameAlert = alertTs == prefs.getLong(WatchConstants.KEY_ALERT_BUZZED_TS, 0L)
        val count  = if (sameAlert) prefs.getInt(WatchConstants.KEY_ALERT_BUZZ_COUNT, 0) else 0
        val lastMs = if (sameAlert) prefs.getLong(WatchConstants.KEY_ALERT_LAST_BUZZ_MS, 0L) else 0L

        if (count >= MAX_BUZZES) return

        val now = System.currentTimeMillis()
        if (lastMs != 0L && now - lastMs < REPEAT_INTERVAL_MS) return

        // Claim first. commit(), not apply() - an async write could let a
        // concurrent caller read stale counters and double-buzz.
        prefs.edit()
            .putLong(WatchConstants.KEY_ALERT_BUZZED_TS, alertTs)
            .putInt(WatchConstants.KEY_ALERT_BUZZ_COUNT, count + 1)
            .putLong(WatchConstants.KEY_ALERT_LAST_BUZZ_MS, now)
            .commit()

        vibrate(context)
        Log.d(TAG, "buzzed for alert ts=$alertTs (${count + 1}/$MAX_BUZZES)")
    }

    // True while an alert is still live and hasn't exhausted its reminders -
    // i.e. whether the nag chain should re-arm itself.
    fun shouldKeepNagging(context: Context): Boolean {
        val prefs = context.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val alertTs = prefs.getLong(WatchConstants.KEY_ALERT_TS, 0L)
        if (alertTs == 0L) return false
        if (alertTs == prefs.getLong(WatchConstants.KEY_ALERT_DISMISSED_TS, 0L)) return false
        if (alertTs != prefs.getLong(WatchConstants.KEY_ALERT_BUZZED_TS, 0L)) return true
        return prefs.getInt(WatchConstants.KEY_ALERT_BUZZ_COUNT, 0) < MAX_BUZZES
    }

    // Double buzz, so it reads as deliberate rather than an ordinary
    // notification. VIBRATE is a normal install-time permission - no runtime
    // prompt needed.
    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
        }.onFailure { Log.e(TAG, "vibrate failed: ${it.message}", it) }
    }
}
