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

    // Complications call refreshFromDataLayer concurrently, so two threads can
    // reach the check at once. Synchronized, and the marker is committed
    // BEFORE vibrating, so the second caller sees the claim and backs off.
    @Synchronized
    fun buzzIfNewAlert(context: Context) {
        val prefs = context.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val alertTs = prefs.getLong(WatchConstants.KEY_ALERT_TS, 0L)

        if (alertTs == 0L) return
        // Already cleared by the user - don't buzz for something they dismissed.
        if (alertTs == prefs.getLong(WatchConstants.KEY_ALERT_DISMISSED_TS, 0L)) return
        // Already buzzed for this exact crossing.
        if (alertTs == prefs.getLong(WatchConstants.KEY_ALERT_BUZZED_TS, 0L)) return

        // Claim it first. commit(), not apply() - an async write could let a
        // concurrent caller read a stale marker and double-buzz.
        prefs.edit().putLong(WatchConstants.KEY_ALERT_BUZZED_TS, alertTs).commit()

        vibrate(context)
        Log.d(TAG, "buzzed for alert ts=$alertTs")
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
