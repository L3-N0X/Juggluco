package tk.glucodata.alerts

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import tk.glucodata.R

/** Sounds shipped with the app, addressed as `android.resource://` URIs. */
object AlertSounds {
    data class BuiltIn(val key: String, val label: String, val resId: Int)

    val builtIns: List<BuiltIn> = listOf(
        BuiltIn("verylow", "Very low", R.raw.verylow),
        BuiltIn("siren", "Siren", R.raw.siren),
        BuiltIn("lowsoon", "Low soon", R.raw.lowsoon),
        BuiltIn("classic", "Classic", R.raw.classic),
        BuiltIn("highsoon", "High soon", R.raw.highsoon),
        BuiltIn("veryhigh", "Very high", R.raw.veryhigh),
        BuiltIn("elves", "Elves", R.raw.elves),
        BuiltIn("ghost", "Ghost", R.raw.ghost),
        BuiltIn("nudge", "Nudge", R.raw.nudge)
    )

    fun uriFor(context: Context, builtIn: BuiltIn): Uri =
        Uri.parse("android.resource://${context.packageName}/${builtIn.resId}")

    /** Built-in sound used when an alert has no sound of its own. */
    fun defaultFor(rule: AlertRule): BuiltIn {
        val key = when (rule.kind) {
            AlertKind.LOW -> when {
                rule.forecastMinutes > 0 -> "lowsoon"
                rule.thresholdMgdl < 60f -> "verylow"
                else -> "siren"
            }
            AlertKind.HIGH -> when {
                rule.forecastMinutes > 0 -> "highsoon"
                rule.thresholdMgdl >= 240f -> "veryhigh"
                else -> "classic"
            }
            AlertKind.FALLING -> "lowsoon"
            AlertKind.RISING -> "highsoon"
            AlertKind.SIGNAL_LOSS -> "elves"
        }
        return builtIns.first { it.key == key }
    }

    fun resolve(context: Context, rule: AlertRule): Uri =
        rule.soundUri?.let { Uri.parse(it) } ?: uriFor(context, defaultFor(rule))

    fun fallback(context: Context, rule: AlertRule): Uri = uriFor(context, defaultFor(rule))

    /** Human readable name of the sound an alert plays. */
    fun label(context: Context, rule: AlertRule): String {
        val uri = rule.soundUri ?: return "${defaultFor(rule).label} (default)"
        builtIns.firstOrNull { uriFor(context, it).toString() == uri }?.let { return it.label }
        return runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
        }.getOrNull() ?: "Custom sound"
    }
}
