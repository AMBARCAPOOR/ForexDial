package com.reddoor3.forexdial.wear

object WatchConstants {
    // Wearable Data Layer paths — must match companion-app Constants
    const val PATH_FOREX = "/forexdial/forex"
    const val PATH_YIELD = "/forexdial/yield"
    const val PATH_SUN   = "/forexdial/sun"

    // DataMap keys
    const val KEY_EURUSD      = "eurusd"
    const val KEY_EURUSD_PREV = "eurusd_prev"
    const val KEY_DXY         = "dxy"
    const val KEY_BTC         = "btc"
    const val KEY_SENTIMENT     = "sentiment"
    const val KEY_YIELD_SPREAD  = "yield_spread"
    // Previous common trading day's spread; Float.MAX_VALUE = unknown.
    const val KEY_YIELD_PREV    = "yield_prev"
    const val KEY_SUNRISE     = "sunrise"
    const val KEY_SUNSET      = "sunset"

    // Price alert (B.4) - see companion Constants for why the alert is keyed
    // by crossing timestamp rather than a boolean.
    const val KEY_ALERT_TS    = "alert_ts"
    const val KEY_ALERT_DIR   = "alert_dir"
    const val KEY_ALERT_LEVEL = "alert_level"

    // Watch-local only: the ts of the alert the user has already cleared.
    // Never sent by the phone - dismissal is entirely a watch-side decision
    // so a tap clears instantly without a phone round-trip.
    const val KEY_ALERT_DISMISSED_TS = "alert_dismissed_ts"

    // SharedPreferences name on watch
    const val PREFS = "watch_forex_cache"
}
