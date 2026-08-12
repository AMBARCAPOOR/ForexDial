package com.reddoor3.forexdial.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Keeps an unacknowledged price alert buzzing until it's dismissed.
//
// Ambar 2026-08-12: "only buzzed once", then "foreground seems more useful".
// He was right, and two earlier attempts were wrong:
//
//   1. Buzz from the DataItem PUSH path - that path is unreliable on this
//      device, so it never ran at all.
//   2. Repeat from the complication POLLING path, assuming the alert
//      complication's 60s UPDATE_PERIOD_SECONDS was a steady heartbeat.
//      Measured: seven calls in three minutes, clustered and irregular. The
//      system only polls complications while the face is being rendered, so
//      with the screen off - exactly when a nag matters - nothing fires.
//
// AlarmManager was the next idea and is also wrong here: on this watch
// (API 36, targetSdk 35) SCHEDULE_EXACT_ALARM is denied by default, so it
// degrades to an inexact alarm that Doze throttles to roughly once every nine
// minutes. It appeared to work only because this particular watch had been put
// on the Doze whitelist during earlier debugging - it would have degraded for
// everyone else installing from the repo.
//
// A foreground service owns its own timer, is exempt from Doze while running,
// and needs no user-granted permission. The ongoing notification is a fair
// trade: an unacknowledged alert should have a visible presence, and it gives
// a second place to dismiss from besides the flashing icon.
class AlertNagService : Service() {

    companion object {
        private const val TAG = "FDD_AlertNag"
        private const val CHANNEL_ID = "forexdial_alert"
        private const val NOTIF_ID = 4711
        private const val INTERVAL_MS = 60_000L

        fun start(ctx: Context) {
            runCatching {
                val i = Intent(ctx, AlertNagService::class.java)
                // startForegroundService, not startService: this is launched
                // from a complication request / receiver, i.e. the background,
                // where a plain start would be refused on modern Android.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            }.onFailure { Log.e(TAG, "start failed: ${it.message}", it) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, AlertNagService::class.java)) }
        }
    }

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after startForegroundService or
        // the system kills us with an ANR-style crash.
        startForeground(NOTIF_ID, buildNotification())

        // Already looping - don't stack a second timer on a repeat start.
        if (scope != null) return START_STICKY

        val s = CoroutineScope(SupervisorJob())
        scope = s
        s.launch {
            while (isActive) {
                if (!AlertBuzzer.shouldKeepNagging(this@AlertNagService)) {
                    Log.d(TAG, "alert resolved or capped - stopping")
                    stopSelf()
                    return@launch
                }
                AlertBuzzer.buzzIfNewAlert(this@AlertNagService)
                delay(INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
        Log.d(TAG, "stopped")
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Price alerts", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "An EUR/USD alert level has been crossed" }
            )
        }

        val prefs = getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val dir = prefs.getString(WatchConstants.KEY_ALERT_DIR, "") ?: ""
        val level = prefs.getFloat(WatchConstants.KEY_ALERT_LEVEL, 0f)
        val text = buildString {
            append(if (dir == "UP") "Crossed up" else "Crossed down")
            if (level > 0f) append(" through %.5f".format(level))
        }

        // Tapping the notification clears the alert, same as tapping the icon.
        val dismiss = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, AlertDismissReceiver::class.java)
                .setAction(AlertDismissReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("EUR/USD alert")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setContentIntent(dismiss)
            .build()
    }
}
