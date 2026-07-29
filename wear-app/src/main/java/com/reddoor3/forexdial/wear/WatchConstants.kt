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
    const val KEY_SUNRISE     = "sunrise"
    const val KEY_SUNSET      = "sunset"

    // SharedPreferences name on watch
    const val PREFS = "watch_forex_cache"
}
