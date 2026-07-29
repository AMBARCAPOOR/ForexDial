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

    // SharedPreferences keys
    const val PREFS_NAME      = "forex_cache"
    const val KEY_EURUSD      = "eurusd_price"
    const val KEY_EURUSD_PREV = "eurusd_prev"
    const val KEY_DXY         = "dxy_price"
    const val KEY_BTC         = "btc_price"
    const val KEY_YIELD_SENT  = "yield_sentiment"
    const val KEY_SUNRISE_EP  = "sunrise_epoch"
    const val KEY_SUNSET_EP   = "sunset_epoch"
}
