package com.reddoor3.forexdial

object Constants {
    // Get your free key at https://finnhub.io — paste it here before first build
    const val FINNHUB_API_KEY = "REDACTED_KEY_SEE_LOCAL_PROPERTIES"

    // Finnhub symbols
    const val SYMBOL_EURUSD = "OANDA:EUR_USD"
    const val SYMBOL_DXY    = "ICEUS:DX1!"          // DXY front-month futures on ICE
    const val SYMBOL_BTC    = "BINANCE:BTCUSDT"

    // Sunrise/sunset location (Los Angeles per spec)
    const val SUN_LAT = "34.0522"
    const val SUN_LNG = "-118.2437"

    // Wearable Data Layer paths (must match WatchConstants in wear-app)
    const val PATH_FOREX = "/forexdial/forex"
    const val PATH_YIELD = "/forexdial/yield"
    const val PATH_SUN   = "/forexdial/sun"

    // DataMap keys
    const val WKEY_EURUSD      = "eurusd"
    const val WKEY_EURUSD_PREV = "eurusd_prev"
    const val WKEY_DXY         = "dxy"
    const val WKEY_BTC         = "btc"
    const val WKEY_SENTIMENT     = "sentiment"
    const val WKEY_YIELD_SPREAD  = "yield_spread"
    const val WKEY_SUNRISE     = "sunrise"
    const val WKEY_SUNSET      = "sunset"

    // Price alert (B.4). The alert is identified by the TIMESTAMP of the
    // crossing that fired it, not a plain boolean: the watch dismisses
    // locally by recording "I've already seen alert <ts>", so the phone can
    // keep re-sending the same fired alert on every 3-minute sync without it
    // popping back up after being cleared. A later crossing produces a new
    // ts, which the watch then treats as a genuinely new alert.
    const val WKEY_ALERT_TS    = "alert_ts"
    const val WKEY_ALERT_DIR   = "alert_dir"    // "UP" | "DOWN"
    const val WKEY_ALERT_LEVEL = "alert_level"

    // SharedPreferences keys
    const val PREFS_NAME      = "forex_cache"
    const val KEY_EURUSD      = "eurusd_price"
    const val KEY_EURUSD_PREV = "eurusd_prev"
    const val KEY_DXY         = "dxy_price"
    const val KEY_BTC         = "btc_price"
    const val KEY_YIELD_SENT  = "yield_sentiment"
    const val KEY_SUNRISE_EP  = "sunrise_epoch"
    const val KEY_SUNSET_EP   = "sunset_epoch"

    // Price alert state, phone side
    const val KEY_ALERT_LEVEL      = "alert_level"       // user-set threshold, 0 = disabled
    const val KEY_ALERT_LAST_PRICE = "alert_last_price"  // previous observation, for crossing detection
    const val KEY_ALERT_FIRED_TS   = "alert_fired_ts"
    const val KEY_ALERT_FIRED_DIR  = "alert_fired_dir"
}
