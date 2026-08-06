# ForexDial

A terminal-style forex watch face for **Wear OS 3+** (built and tested on a Samsung Galaxy Watch 7, Wear OS 6), with a phone companion app that fetches the data.

Shows EUR/USD with pip-level precision, the DXY dollar index, BTC, the EUR–US 2-year yield spread, live market-session status for XETRA / LSE / NYSE, and a price alert that buzzes your wrist and flashes on screen when EUR/USD crosses a level you set.

---

## You need your own API keys

**This is not optional and there is no shared key.** Two of the data feeds are metered, and the free tiers are sized for roughly one user each:

- Twelve Data's free tier allows **800 credits/day**. ForexDial's default polling uses about **720/day** — so a single key realistically serves one person.

Keys are read from `local.properties`, which is gitignored and **never committed**. A fresh clone builds with no key baked in, so you must supply your own before anything works.

Both are free and take a couple of minutes:

| Service | Used for | Sign up |
|---|---|---|
| **Twelve Data** | EUR/USD, DXY basket | <https://twelvedata.com> |
| **Finnhub** | BTC | <https://finnhub.io> |

The yield spread needs **no key** — it comes from the ECB and FRED, which are open. That part of the face works even with nothing configured.

---

## Setup

### 1. Add your keys

Create or edit `local.properties` in the project root:

```properties
sdk.dir=/path/to/your/Android/Sdk
twelveDataApiKey=your_twelve_data_key_here
finnhubApiKey=your_finnhub_key_here
```

`sdk.dir` is written automatically by Android Studio the first time you open the project. Add the two key lines yourself.

If you skip this, the app still builds and runs — it just shows a message telling you which keys are missing.

### 2. Build

```bash
./gradlew assembleDebug
```

### 3. Install

There are **three** packages, and you need all three. This is a platform constraint, not a design choice — see [Why three packages](#why-three-packages).

Phone (connect over USB, enable USB debugging):

```bash
adb -s <phone-serial> install -r companion-app/build/outputs/apk/debug/companion-app-debug.apk
```

Watch (enable Wireless debugging in Developer options, then `adb pair` / `adb connect`):

```bash
adb -s <watch-addr> install -r wear-app/build/outputs/apk/debug/wear-app-debug.apk
adb -s <watch-addr> install -r watchface-wff/build/outputs/apk/debug/watchface-wff-debug.apk
```

If a streamed install fails on the watch (it sometimes does over wireless ADB), push and install locally instead:

```bash
adb -s <watch-addr> push watchface-wff/build/outputs/apk/debug/watchface-wff-debug.apk /data/local/tmp/wf.apk
adb -s <watch-addr> shell pm install -r /data/local/tmp/wf.apk
```

### 4. Select the watch face

Long-press the watch face → swipe to **ForexDial** → tap to select.

---

## Using it

**Data refresh.** The phone polls prices every 3 minutes and pushes to the watch. Tapping anywhere on the face requests an immediate sync rather than just re-reading the local cache.

**Price alerts.** In the phone app, enter a level under *EUR/USD price alert* and hit save. When EUR/USD **crosses** that level in either direction, the watch buzzes twice and a flashing bell + direction arrow appears. **Tap the icon on the watch to clear it.**

It fires on the *crossing*, not on merely being past the level — so it alerts once per crossing rather than nagging every sync for as long as price stays beyond your threshold. Leave the field blank or `0` to disable.

**Reading the face:**

- **Cyan = up, orange = down** throughout (this is a personal convention, not the usual green/red).
- **EUR/USD** — last two digits are larger and direction-coloured; the box border tracks intraday direction.
- **YIELD** — the EUR−US 2-year spread, coloured by **day-over-day change**: cyan if the spread rose (EUR rate advantage improved), orange if it fell. Note this is the *direction of the move*, not the level — orange on a negative spread means "got worse for EUR today."
- **Battery candle** — cyan above 50%, amber 20–50%, red below 20%.
- **Session dials** — green open, orange within an hour of open/close, red closed. Local exchange time inside each.

---

## Data sources

| Value | Source | Frequency | Key? |
|---|---|---|---|
| EUR/USD | Twelve Data | 3 min | Yes |
| DXY | Twelve Data (5-pair basket, approximated) | basket cached 30 min | Yes |
| BTC | Finnhub | 3 min | Yes |
| EUR 2Y yield | ECB Data Portal (AAA euro-area curve) | 02:00 & 14:00 PT | No |
| US 2Y yield | FRED (`DGS2`) | 02:00 & 14:00 PT | No |
| Session status | Local calendar maths | continuous | No |

The yield legs are daily series, so they're polled twice a day rather than continuously. The spread is computed on the most recent day **both** sources cover — they publish on different lags, and pairing newest-with-newest silently spans two dates (measured ~4bp of error that way, enough to fake a day's move).

---

## Why three packages

Wear OS's **Watch Face Format** (WFF) is declarative XML with `android:hasCode="false"` — a watch face APK contains no executable code at all. Since this face renders custom bitmaps from live market data, the complication data sources must be Kotlin services, which cannot live in the watch face package. Hence:

| Package | Runs on | Contains |
|---|---|---|
| `companion-app` | Phone | API fetching, alert configuration, pushes to watch |
| `wear-app` | Watch | Complication data sources (the rendered bitmaps) |
| `watchface-wff` | Watch | The watch face layout itself (WFF XML) |

Google's own WFF samples follow the same constraint — even their Weather sample ships `hasCode="false"` with no services, relying on a built-in platform complication.

---

## Limitations

- **Sideload only.** Not on Google Play. Installing on Wear OS requires ADB.
- **One user per key.** See above — the free tiers don't stretch.
- **EUR/USD only.** The pair isn't configurable yet.
- **Wear OS 3+.** Wear OS 6 blocks programmatic (Kotlin) watch faces entirely, which is why this is WFF.
- Built against one device (Galaxy Watch 7, 450×450 round). Layout coordinates are tuned for that; other sizes will need adjustment.

---

## License

Personal hobby project, provided as-is with no warranty or support. Do your own diligence before relying on any number here for trading decisions.
