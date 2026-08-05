package com.reddoor3.forexdial.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.reddoor3.forexdial.wear.complication.AlertComplicationService

// Clears a fired price alert. Wired to the alert icon's own tap action, so
// tapping the flashing bell dismisses it (Ambar 2026-08-05: "there should be
// a way for me to clear it ... it cant be there the whole time").
//
// Dismissal is purely watch-local and needs no phone round-trip: we record
// the ts of the alert being cleared, and the complication then treats any
// alert with that same ts as already-seen. The phone goes on re-sending that
// same fired alert on every sync (it has no idea we dismissed it) and it
// correctly stays hidden - while a LATER crossing carries a new ts and so
// shows up as a genuinely new alert.
class AlertDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.reddoor3.forexdial.wear.ACTION_ALERT_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return

        val prefs = context.getSharedPreferences(WatchConstants.PREFS, Context.MODE_PRIVATE)
        val activeTs = prefs.getLong(WatchConstants.KEY_ALERT_TS, 0L)
        if (activeTs == 0L) return

        prefs.edit().putLong(WatchConstants.KEY_ALERT_DISMISSED_TS, activeTs).commit()

        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, AlertComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
