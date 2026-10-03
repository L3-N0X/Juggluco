package tk.glucodata.ui.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.R

/**
 * Replaces the label names native starts out with by ones people actually use.
 *
 * Native fills `settings.dat` with seven labels in the language of the first start: carbs,
 * dextro, rapid, long, bike, walk, blood, cut to 11 bytes ("Kohlenhydra", "Ins schnell",
 * "radeln"). Each position keeps its meaning here, so entries saved under a label stay what they
 * were; only the name changes, and it gains a full name next to its short one ([LabelNames]).
 *
 * A label is only renamed while it still carries one of native's default names, in any language,
 * so a name the user picked is never touched. The one exception to "same meaning" is the walk
 * label: it becomes ketones, and so only while nothing was ever logged under it.
 *
 * Runs once, and only where the app ships these names in the language the device is set to
 * (`label_defaults_language`): a French phone keeps native's French labels rather than getting
 * English ones.
 */
internal object LabelDefaults {
    private const val LOG_ID = "LabelDefaults"
    private const val PREFS = "label_defaults"
    private const val KEY_APPLIED = "applied_v1"

    private class Default(
        @param:StringRes val full: Int,
        @param:StringRes val short: Int,
        /** Every name native ever started this position with. */
        val legacy: Set<String>,
        /** The Garmin rounding step to use instead of the one native set, if any. */
        val roundTo: Float? = null,
        /** Whether the position changes meaning, so it may only be renamed while unused. */
        val newMeaning: Boolean = false
    )

    private val defaults = listOf(
        Default(
            R.string.label_default_carbs, R.string.label_default_carbs_short,
            setOf(
                "Carbohydra", "Kohlenhydra", "Carbohidr", "Glucides", "Carb", "Carboidrati", "קרבוהידרה",
                "糖質", "탄수", "Koolhydraat", "Węglowodan", "Carbohidra", "Угл.", "Kolhydrater",
                "Karbonhidrt", "Вугл.", "Uglevod", "碳水化合物", "Вугляв", "كرب"
            )
        ),
        Default(
            R.string.label_default_hypo, R.string.label_default_hypo_short,
            setOf(
                "Dextro", "Dextrose", "Glucosio", "דקסטרו", "葡萄糖", "포도당", "Glicemia", "Сахар",
                "Дек.", "右旋糖酐", "Дэкстр", "دكس"
            )
        ),
        Default(
            R.string.label_default_bolus, R.string.label_default_bolus_short,
            setOf(
                "Fast Insuli", "Ins schnell", "Insulina r", "Ins. rapide", "Tez Insuli", "Rapida",
                "מהיר Insuli", "速効型", "속효성", "Insuli snel", "Insul szybk", "Insu Rápida", "Болус",
                "Insulin", "HizliEtkili", "Швидк.", "Tez insulin", "快速胰岛素", "Хуткі", "سري"
            )
        ),
        Default(
            R.string.label_default_basal, R.string.label_default_basal_short,
            setOf(
                "Long Insuli", "Langes Insu", "Insulina l", "Ins. basale", "Lambi Insul", "Lenta",
                "לונג אינסולי", "持効型", "지속형", "Insuli lang", "Insul dług", "Insul Lenta", "Базал",
                "Basinsulin", "Uzun Etkili", "Довг.", "Bazal ins.", "长效胰岛素", "Доўгі", "طوي"
            )
        ),
        Default(
            R.string.label_default_exercise, R.string.label_default_exercise_short,
            setOf(
                "Bike", "radeln", "Bici", "Vélo", "Cycle", "אופניים", "自転車", "자전거", "Fietsen", "Rower",
                "Bicicleta", "Вело", "Cykling", "Bisiklet", "Велос", "Velosiped", "骑自行车", "Ровар", "درج"
            )
        ),
        Default(
            R.string.label_default_ketones, R.string.label_default_ketones_short,
            setOf(
                "Walk", "Caminar", "Marche", "Paidal", "ללכת", "散歩", "걷기", "Wandelen", "Spacer",
                "Caminhada", "Шаги", "Promenad", "Yürüyüş", "Прог.", "Yurish", "步行", "Прагул", "مشي"
            ),
            roundTo = 0.1f,
            newMeaning = true
        ),
        Default(
            R.string.label_default_finger_prick, R.string.label_default_finger_prick_short,
            setOf(
                "Blood", "Blut", "Sangre", "Sang", "Khoon", "Capillare", "דם", "血糖", "혈당", "Bloed",
                "Krew", "Sangue", "Кровь", "Blod", "Kan Degeri", "Кров", "Qon", "血液", "Кроў", "دم"
            )
        )
    )

    /**
     * Renames the labels that still carry a native default. Returns whether any label changed, in
     * which case the caller hands the labels on to mirrors as after any other edit.
     */
    fun apply(labelInUse: (Int) -> Boolean): Boolean {
        val app = Applic.app ?: return false
        val prefs = try { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE) } catch (_: Throwable) { return false }
        if (prefs.getBoolean(KEY_APPLIED, false)) return false
        val editable = try { Applic.Nativesloaded && !Natives.staticnum() } catch (_: Throwable) { false }
        if (!editable) return false
        // The language the strings below actually resolve in, which an in-app language sets too.
        val language = ConfigurationCompat.getLocales(app.resources.configuration)[0]?.language
        if (app.getString(R.string.label_defaults_language) != language) return false

        val names = try { Natives.getLabels().orEmpty().dropLast(1) } catch (_: Throwable) { return false }
        var changed = false
        defaults.forEachIndexed { index, default ->
            val current = names.getOrNull(index) ?: return@forEachIndexed
            if (!isLegacy(current, default.legacy)) return@forEachIndexed
            if (default.newMeaning && labelInUse(index)) return@forEachIndexed
            val full = app.getString(default.full)
            val short = LabelNames.fitShort(app.getString(default.short))
            val saved = try {
                Natives.setlabel(
                    index,
                    short,
                    default.roundTo ?: Natives.getprec(index),
                    Natives.getweight(index).coerceAtLeast(0f)
                )
            } catch (th: Throwable) {
                Log.stack(LOG_ID, "apply", th)
                false
            }
            if (saved) {
                LabelNames.store(index, short, full)
                changed = true
            }
        }
        prefs.edit().putBoolean(KEY_APPLIED, true).apply()
        if (changed) Log.i(LOG_ID, "Replaced the native default label names")
        return changed
    }

    /**
     * Whether [name] is one of native's defaults. A few of them never fitted their 11 bytes and
     * come back cut short (possibly in the middle of a character), so those match by prefix.
     */
    private fun isLegacy(name: String, legacy: Set<String>): Boolean {
        if (name in legacy) return true
        val clean = name.trimEnd('�')
        if (LabelNames.nativeBytes(clean) < 6) return false
        return legacy.any { LabelNames.nativeBytes(it) > LabelNames.MAX_SHORT_BYTES && it.startsWith(clean) }
    }
}
