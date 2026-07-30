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
import com.reddoor3.forexdial.work.ForexSyncWorker
import com.reddoor3.forexdial.work.YieldSpreadWorker
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var valuesText: TextView
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
            text = "ForexDial"
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
            append("Last sync: ${if (lastSync == 0L) "Never" else fmt.format(Date(lastSync))}\n")
            if (started > lastSync && started > 0L) append("Worker started: ${fmt.format(Date(started))}\n")
            if (syncStatus.isNotEmpty()) append("Status: $syncStatus")
        }

        valuesText.text = buildString {
            append("EUR/USD: ${if (eurUsd == 0f) "—" else "%.5f".format(eurUsd)}\n")
            append("DXY:     ${if (dxy    == 0f) "—" else "%.2f".format(dxy)}\n")
            append("BTC:     ${if (btc    == 0f) "—" else "$%.0f".format(btc)}")
        }
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
