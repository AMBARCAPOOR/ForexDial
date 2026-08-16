package com.reddoor3.forexdial

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.reddoor3.forexdial.api.ApiKeys
import com.reddoor3.forexdial.work.ForexSyncWorker
import com.reddoor3.forexdial.work.YieldSpreadWorker
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var valuesText: TextView
    private lateinit var alertInput: EditText
    private lateinit var alertInput2: EditText
    private lateinit var alertStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDisplay()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 100, 64, 64)
        }

        TextView(this).apply {
            // Version on screen, not just in the build file - so a bug report
            // can name a build without anyone having to dig through Settings.
            text = "ForexDial  v${BuildConfig.VERSION_NAME}"
            textSize = 26f
            root.addView(this)
        }

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 20, 0, 4)
            root.addView(this)
        }

        valuesText = TextView(this).apply {
            textSize = 15f
            setPadding(0, 8, 0, 24)
            root.addView(this)
        }

        Button(this).apply {
            text = "Sync Now"
            setOnClickListener { triggerSync() }
            root.addView(this)
        }

        // ---- Price alert (B.4) ----
        TextView(this).apply {
            text = "EUR/USD price alert"
            textSize = 18f
            setPadding(0, 48, 0, 4)
            root.addView(this)
        }

        TextView(this).apply {
            text = "Alerts on CROSSING a level in either direction — so one level " +
                   "catches both a break up and a break down. Blank or 0 disables " +
                   "that slot. Clear a fired alert by tapping the icon on the watch."
            textSize = 12f
            setPadding(0, 0, 0, 12)
            root.addView(this)
        }

        val saved = AlertLevels.read(getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE))

        fun levelField(index: Int) = EditText(this).apply {
            hint = if (index == 0) "Level 1 — e.g. 1.16000" else "Level 2 — e.g. 1.15000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            saved.getOrNull(index)?.let { setText("%.5f".format(it)) }
            root.addView(this)
        }

        alertInput  = levelField(0)
        alertInput2 = levelField(1)

        alertStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 8, 0, 8)
            root.addView(this)
        }

        Button(this).apply {
            text = "Save alert level"
            setOnClickListener { saveAlertLevel() }
            root.addView(this)
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun refreshDisplay() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val lastSync  = prefs.getLong("last_sync_ts", 0L)
        val started   = prefs.getLong("sync_started_ts", 0L)
        val syncStatus = prefs.getString("sync_status", "") ?: ""
        val eurUsd = prefs.getFloat("cached_eurusd", 0f)
        val dxy    = prefs.getFloat("cached_dxy", 0f)
        val btc    = prefs.getFloat("cached_btc", 0f)

        statusText.text = buildString {
            // Setup problems come first - if there's no key, everything below
            // is a symptom of that rather than an independent failure.
            val hint = ApiKeys.setupHint()
            if (hint.isNotEmpty()) append("$hint\n\n")
            append("Last sync: ${if (lastSync == 0L) "Never" else fmt.format(Date(lastSync))}\n")
            if (started > lastSync && started > 0L) append("Worker started: ${fmt.format(Date(started))}\n")
            if (syncStatus.isNotEmpty()) append("Status: $syncStatus")
        }

        valuesText.text = buildString {
            append("EUR/USD: ${if (eurUsd == 0f) "—" else "%.5f".format(eurUsd)}\n")
            append("DXY:     ${if (dxy    == 0f) "—" else "%.2f".format(dxy)}\n")
            append("BTC:     ${if (btc    == 0f) "—" else "$%.0f".format(btc)}")
        }

        val levels     = AlertLevels.read(prefs)
        val firedTs    = prefs.getLong(Constants.KEY_ALERT_FIRED_TS, 0L)
        val firedDir   = prefs.getString(Constants.KEY_ALERT_FIRED_DIR, "") ?: ""
        val firedLevel = prefs.getFloat(Constants.KEY_ALERT_FIRED_LEVEL, 0f)
        alertStatus.text = buildString {
            append(
                if (levels.isEmpty()) "Alerts: off"
                else "Armed: " + levels.joinToString(", ") { "%.5f".format(it) }
            )
            if (firedTs > 0L) {
                append("\nLast fired: $firedDir")
                if (firedLevel > 0f) append(" through %.5f".format(firedLevel))
                append(" at ${fmt.format(Date(firedTs))}")
            }
        }
    }

    private fun saveAlertLevel() {
        val levels = mutableListOf<Float>()
        for (field in listOf(alertInput, alertInput2)) {
            val raw = field.text.toString().trim()
            if (raw.isEmpty()) continue          // blank slot = unused
            val v = raw.toFloatOrNull()
            if (v == null) {
                Toast.makeText(this, "\"$raw\" is not a valid number", Toast.LENGTH_SHORT).show()
                return                            // reject the whole save rather
            }                                     // than silently dropping one
            if (v > 0f) levels.add(v)
        }

        AlertLevels.write(getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE), levels)

        Toast.makeText(
            this,
            when (levels.size) {
                0 -> "Alerts disabled"
                1 -> "Armed at %.5f".format(levels[0])
                else -> "Armed at ${levels.joinToString(" and ") { "%.5f".format(it) }}"
            },
            Toast.LENGTH_SHORT
        ).show()
        refreshDisplay()
    }

    private fun triggerSync() {
        // Diagnosed 2026-07-30: the "significant delay" after tapping Sync Now
        // was because a plain OneTimeWorkRequest is regular background work -
        // WorkManager is free to batch/delay it under normal Doze/JobScheduler
        // throttling, same as any other background job. setExpedited() is
        // Android's actual mechanism for "user just tapped a button, run this
        // now" (WorkManager 2.7+); RUN_AS_NON_EXPEDITED_WORK_REQUEST falls back
        // to normal scheduling if the expedited quota is exhausted, so this is
        // safe to always request.
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(applicationContext).apply {
            enqueue(OneTimeWorkRequestBuilder<ForexSyncWorker>()
                .setConstraints(net)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build())
            enqueue(OneTimeWorkRequestBuilder<YieldSpreadWorker>()
                .setConstraints(net)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build())
        }
        Toast.makeText(this, "Sync triggered", Toast.LENGTH_SHORT).show()
    }
}
