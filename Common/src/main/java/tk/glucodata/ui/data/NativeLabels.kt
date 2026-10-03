package tk.glucodata.ui.data

import android.content.Context
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.ui.model.LabelConfig
import tk.glucodata.ui.model.LogLabel
import tk.glucodata.ui.model.LogType
import java.util.Locale

/**
 * Reads the native labels and decides which of them carbs, bolus, basal and finger-pricks are
 * saved under.
 *
 * Carbs and finger-pricks follow the native meal and blood labels. Bolus and basal have no native
 * setting, so the choice is kept here. Until the user makes one it is inferred, in order of how
 * deliberate the source is: the Nightscout/LibreView treatment mapping, the IOB insulin types,
 * the label names, and finally the position labels have in the native defaults of every language
 * (carbs, dextro, rapid, long, ...). The inferred choice is stored the first time it is made, so
 * renaming a label later never moves entries to another type behind the user's back.
 *
 * A device that mirrors another one's labels does not store an inferred choice: it takes the one
 * of the device the labels come from (see [tk.glucodata.ui.sync.DisplaySync]) and only infers in
 * the meantime, from the same synced native settings.
 */
internal object NativeLabels {
    private const val LOG_ID = "NativeLabels"
    private const val PREFS = "log_labels"
    private const val KEY_BOLUS = "bolus_label"
    private const val KEY_BASAL = "basal_label"
    private const val UNSET = -1

    /** The native default label set: carbs, dextro, rapid, long, bike, walk, blood. */
    private const val DEFAULT_BOLUS_POSITION = 2
    private const val DEFAULT_BASAL_POSITION = 3

    private val bolusWords = listOf(
        "bolus", "болус", "fast", "rapid", "quick", "kurz", "schnell", "snel", "rapide", "rápida", "rapida",
        "insulina r", "szybk", "hizli", "hızlı", "tez ", "хутк", "швидк", "novo", "aspart", "humalog", "lispro",
        "fiasp", "lyumjev", "apidra", "actrapid", "admelog", "速効", "快速"
    )
    private val basalWords = listOf(
        "basal", "bazal", "базал", "long", "lang", "slow", "lenta", "lente", "basale", "insulina l", "dług",
        "uzun", "lambi", "довг", "доўг", "levemir", "lantus", "tresiba", "toujeo", "abasaglar", "semglee",
        "持効", "长效"
    )

    fun read(): LabelConfig {
        val shortNames = try { Natives.getLabels().orEmpty() } catch (_: Throwable) { emptyList() }
            // Native always appends an empty entry for "no label".
            .dropLast(1)
            .map { it.orEmpty() }
        // Native holds the short names; the full ones are kept beside them, see [LabelNames].
        val names = shortNames.mapIndexed { index, short -> LabelNames.fullName(index, short) }
        val count = names.size
        val editable = try { !Natives.staticnum() } catch (_: Throwable) { true }
        val carbs = (try { Natives.getmealvar().toInt() } catch (_: Throwable) { UNSET })
            .takeIf { it in 0 until count } ?: inferCarbs(names)
        val blood = (try { Natives.getbloodvar().toInt() } catch (_: Throwable) { UNSET })
            .takeIf { it in 0 until count && it != carbs } ?: UNSET

        val taken = setOf(carbs, blood)
        val prefs = preferences()
        var bolus = prefs?.getInt(KEY_BOLUS, UNSET) ?: UNSET
        var basal = prefs?.getInt(KEY_BASAL, UNSET) ?: UNSET
        if (bolus !in 0 until count || bolus in taken) bolus = UNSET
        if (basal !in 0 until count || basal in taken || basal == bolus) basal = UNSET
        if ((bolus == UNSET || basal == UNSET) && count > 0) {
            val (inferredBolus, inferredBasal) = inferInsulin(names, taken, bolus, basal)
            if (bolus == UNSET) bolus = inferredBolus
            if (basal == UNSET) basal = inferredBasal
            if (editable) store(bolus, basal)
        }

        val provisional = LabelConfig(
            carbsLabel = carbs,
            bolusLabel = bolus,
            basalLabel = basal,
            bloodLabel = blood,
            labels = names.mapIndexed { index, name ->
                LogLabel(index, name, shortNames[index], LogType.CUSTOM, weight = 0f, roundTo = 0f)
            }
        )
        return provisional.copy(
            labels = provisional.labels.map { label ->
                label.copy(
                    type = provisional.typeOf(label.index),
                    weight = try { Natives.getweight(label.index) } catch (_: Throwable) { 0f },
                    roundTo = try { Natives.getprec(label.index) } catch (_: Throwable) { 0f }
                )
            },
            editable = editable,
            hasGarmin = try { Natives.gethasgarmin() } catch (_: Throwable) { false }
        )
    }

    /** The user picked the label of bolus or basal. */
    fun setInsulinLabel(type: LogType, index: Int) {
        val key = when (type) {
            LogType.RAPID_INSULIN -> KEY_BOLUS
            LogType.BASAL_INSULIN -> KEY_BASAL
            else -> return
        }
        try {
            preferences()?.edit()?.putInt(key, index)?.apply()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "setInsulinLabel", th)
        }
    }

    /** The bolus and basal labels as last stored, for handing to a mirroring device. */
    fun storedInsulinLabels(): Pair<Int, Int> {
        val prefs = preferences()
        return (prefs?.getInt(KEY_BOLUS, UNSET) ?: UNSET) to (prefs?.getInt(KEY_BASAL, UNSET) ?: UNSET)
    }

    /** Takes over the choice of the device this one mirrors. Returns whether anything changed. */
    fun applyMirroredInsulinLabels(bolus: Int, basal: Int): Boolean {
        if (storedInsulinLabels() == (bolus to basal)) return false
        store(bolus, basal)
        return true
    }

    private fun store(bolus: Int, basal: Int) {
        try {
            preferences()?.edit()?.putInt(KEY_BOLUS, bolus)?.putInt(KEY_BASAL, basal)?.apply()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "store", th)
        }
    }

    private fun preferences() = try {
        Applic.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private fun inferCarbs(names: List<String>): Int {
        if (names.isEmpty()) return UNSET
        for (index in names.indices) {
            if (treatmentKind(index) == KIND_CARBS) return index
        }
        return 0
    }

    private fun inferInsulin(names: List<String>, taken: Set<Int>, bolusIn: Int, basalIn: Int): Pair<Int, Int> {
        val free = names.indices.filter { it !in taken }
        var bolus = bolusIn
        var basal = basalIn
        fun pick(current: Int, other: Int, test: (Int) -> Boolean): Int =
            if (current != UNSET) current else free.firstOrNull { it != other && test(it) } ?: UNSET

        bolus = pick(bolus, basal) { treatmentKind(it) == KIND_RAPID }
        basal = pick(basal, bolus) { treatmentKind(it) == KIND_LONG }
        bolus = pick(bolus, basal) { insulinType(it) != 0 }
        bolus = pick(bolus, basal) { nameMatches(names[it], bolusWords) && !nameMatches(names[it], basalWords) }
        basal = pick(basal, bolus) { nameMatches(names[it], basalWords) && !nameMatches(names[it], bolusWords) }
        bolus = pick(bolus, basal) { it == DEFAULT_BOLUS_POSITION }
        basal = pick(basal, bolus) { it == DEFAULT_BASAL_POSITION }
        return bolus to basal
    }

    private fun nameMatches(name: String, words: List<String>): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return words.any { lower.contains(it) }
    }

    /** The Nightscout mapping first, then the LibreView one: 1 rapid, 2 long, 3 carbs. */
    private fun treatmentKind(index: Int): Int {
        for (night in intArrayOf(1, 0)) {
            val kind = try { Natives.getlibrenumkind(night, index) } catch (_: Throwable) { 0 }
            if (kind in KIND_RAPID..KIND_CARBS) return kind
        }
        return 0
    }

    /** The IOB insulin type of a label; only rapid acting insulins can be picked there. */
    private fun insulinType(index: Int): Int =
        try { Natives.getInsulinType(index) } catch (_: Throwable) { 0 }

    private const val KIND_RAPID = 1
    private const val KIND_LONG = 2
    private const val KIND_CARBS = 3
}
