package com.example.focusnest

/** One phase of the cycle. The focus length is set by the user; breaks are fixed. */
enum class Phase(val label: String) {
    FOCUS("Focus"),
    SHORT_BREAK("Short break"),
    LONG_BREAK("Long break");

    fun minutes(): Int = when (this) {
        FOCUS -> AppSettings.focusMinutes
        SHORT_BREAK -> 5
        LONG_BREAK -> 15
    }
}

/** Background sounds bundled as raw resources. Lofi tracks by Holizna, CC0 (public domain). */
enum class Music(val label: String, val resId: Int?) {
    NONE("No sound", null),
    RAIN("Rain", R.raw.rain_loop),
    FIRST_SNOW("First Snow", R.raw.lofi_first_snow),
    KEEPING_COOL("Keeping Cool", R.raw.lofi_keeping_cool),
    TWO_HOUR_DELAY("2 Hour Delay", R.raw.lofi_two_hour_delay),
    LAUNDRY_ON_THE_WIRE("Laundry On The Wire", R.raw.lofi_laundry_on_the_wire),
    EVERYTHING_YOU_EVER_DREAMED("Everything You Ever Dreamed", R.raw.lofi_everything_you_ever_dreamed),
    SNOW_DRIFT("Snow Drift", R.raw.lofi_snow_drift),
    WINDOWS_DOWN("Windows Down", R.raw.lofi_windows_down)
}

/** mm:ss, rounded up so a 25:00 timer shows 25:00 at the start. */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** For example "1h 25m". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return "${minutes / 60}h ${minutes % 60}m"
}
