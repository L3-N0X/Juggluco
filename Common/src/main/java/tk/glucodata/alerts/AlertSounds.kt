package tk.glucodata.alerts

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.StringRes
import tk.glucodata.R

/** Sounds shipped with the app, addressed as `android.resource://` URIs. */
object AlertSounds {
    data class BuiltIn(val key: String, @StringRes val labelRes: Int, val resId: Int)

    val builtIns: List<BuiltIn> = listOf(
        BuiltIn("verylow", R.string.loc_sound_very_low, R.raw.verylow),
        BuiltIn("siren", R.string.loc_sound_siren, R.raw.siren),
        BuiltIn("lowsoon", R.string.loc_sound_low_soon, R.raw.lowsoon),
        BuiltIn("classic", R.string.loc_sound_classic, R.raw.classic),
        BuiltIn("highsoon", R.string.loc_sound_high_soon, R.raw.highsoon),
        BuiltIn("veryhigh", R.string.loc_sound_very_high, R.raw.veryhigh),
        BuiltIn("elves", R.string.loc_sound_elves, R.raw.elves),
        BuiltIn("ghost", R.string.loc_sound_ghost, R.raw.ghost),
        BuiltIn("nudge", R.string.loc_sound_nudge, R.raw.nudge)
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
        val default = defaultFor(rule)
        val uri = rule.soundUri ?: return context.getString(R.string.loc_default_suffix, context.getString(default.labelRes))
        builtIns.firstOrNull { uriFor(context, it).toString() == uri }?.let { return context.getString(it.labelRes) }
        return runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
        }.getOrNull() ?: context.getString(R.string.loc_sound_custom)
    }
}
