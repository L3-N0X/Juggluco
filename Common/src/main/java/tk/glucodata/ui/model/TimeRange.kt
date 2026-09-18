package tk.glucodata.ui.model

enum class TimeRange(val label: String, val durationMillis: Long) {
    THREE_HOURS("3h", 3 * 3600 * 1000L),
    SIX_HOURS("6h", 6 * 3600 * 1000L),
    TWELVE_HOURS("12h", 12 * 3600 * 1000L),
    TWENTY_FOUR_HOURS("24h", 24 * 3600 * 1000L),
    SEVEN_DAYS("7d", 7 * 24 * 3600 * 1000L)
}
