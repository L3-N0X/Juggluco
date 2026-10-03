package tk.glucodata.ui.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tk.glucodata.Applic
import tk.glucodata.Log
import java.text.BreakIterator

/**
 * The full names of the logbook labels.
 *
 * Native keeps a label's name in a `char[12]` inside `settings.dat`, which is also what the
 * classic view, Garmin, Nightscout and every mirroring Juggluco read. That field stays as it is
 * and holds the label's *short* name. The full name the user typed lives here, next to the short
 * name it was saved with: a full name only applies while native still holds that same short
 * name, so a rename made elsewhere (the classic label screen, a phone this device mirrors) shows
 * up as it is instead of being covered by a name that no longer belongs to it.
 *
 * Only names that differ from their short name are stored. A device that mirrors another one's
 * labels takes the whole set from it (see [tk.glucodata.ui.sync.DisplaySync]).
 */
internal object LabelNames {
    private const val LOG_ID = "LabelNames"
    private const val PREFS = "label_names"
    private const val KEY_NAMES = "names"

    /** What native holds of a name: `char[12]`, so 11 bytes of (modified) UTF-8 plus the end. */
    const val MAX_SHORT_BYTES = 11

    /** Long enough that nobody runs into it, short enough to stay a name. */
    const val MAX_NAME_LENGTH = 64

    private data class Entry(val short: String, val full: String)

    @Volatile
    private var cache: Map<Int, Entry>? = null

    /** The full name of label [index], whose native (short) name is [short]. */
    fun fullName(index: Int, short: String): String {
        val entry = entries()[index] ?: return short
        return if (entry.short == short) entry.full else short
    }

    /** Remembers [full] as the name of label [index], which native holds as [short]. */
    fun store(index: Int, short: String, full: String) {
        val next = entries().toMutableMap()
        if (full.isEmpty() || full == short) next.remove(index) else next[index] = Entry(short, full)
        write(next)
    }

    /** Native dropped label [index] (only the last one can go). */
    fun forget(index: Int) {
        val current = entries()
        if (index !in current) return
        write(current - index)
    }

    /** Every stored name, for handing to a device that mirrors this one's labels. */
    fun snapshot(): JSONArray = toJson(entries())

    /** Takes over the names of the device this one mirrors. Returns whether anything changed. */
    fun applyMirrored(json: JSONArray): Boolean {
        val next = fromJson(json)
        if (next == entries()) return false
        write(next)
        return true
    }

    // --- Short names ------------------------------------------------------------------

    /**
     * The bytes native counts for [text]: JNI hands strings over as modified UTF-8, where a
     * character outside the basic plane takes six bytes (two surrogates of three) instead of four.
     */
    fun nativeBytes(text: CharSequence): Int {
        var bytes = 0
        for (c in text) {
            bytes += when {
                c.code == 0 -> 2
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                else -> 3
            }
        }
        return bytes
    }

    fun fitsShort(text: String): Boolean = nativeBytes(text) <= MAX_SHORT_BYTES

    /** [text] cut down to what native holds, never splitting a character or an emoji. */
    fun fitShort(text: String, maxBytes: Int = MAX_SHORT_BYTES): String {
        val trimmed = text.trim()
        if (nativeBytes(trimmed) <= maxBytes) return trimmed
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
        var end = 0
        var next = boundaries.next()
        while (next != BreakIterator.DONE && nativeBytes(trimmed.subSequence(0, next)) <= maxBytes) {
            end = next
            next = boundaries.next()
        }
        return trimmed.substring(0, end).trimEnd()
    }

    /**
     * The short name offered for [full] until the user types their own: the name itself when it
     * fits, else its first word ("Bolus insulin" → "Bolus"), else the name cut short with a dot.
     */
    fun deriveShort(full: String): String {
        val trimmed = full.trim()
        if (fitsShort(trimmed)) return trimmed
        val firstWord = trimmed.split(WORD_BREAK).firstOrNull { it.isNotEmpty() }
        if (firstWord != null && firstWord.codePointCount(0, firstWord.length) >= 3 && fitsShort(firstWord)) {
            return firstWord
        }
        val cut = fitShort(trimmed, MAX_SHORT_BYTES - 1)
        return if (cut.isEmpty()) "" else "$cut."
    }

    private val WORD_BREAK = Regex("[\\s\\-/_,.()]+")

    // --- Storage ----------------------------------------------------------------------

    private fun entries(): Map<Int, Entry> {
        cache?.let { return it }
        val read = try {
            preferences()?.getString(KEY_NAMES, null)?.let { fromJson(JSONArray(it)) }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "entries", th)
            null
        }.orEmpty()
        cache = read
        return read
    }

    private fun write(entries: Map<Int, Entry>) {
        cache = entries
        try {
            preferences()?.edit()?.putString(KEY_NAMES, toJson(entries).toString())?.apply()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "write", th)
        }
    }

    private fun toJson(entries: Map<Int, Entry>): JSONArray = JSONArray().apply {
        entries.toSortedMap().forEach { (index, entry) ->
            put(JSONObject().put("i", index).put("s", entry.short).put("f", entry.full))
        }
    }

    private fun fromJson(json: JSONArray): Map<Int, Entry> = buildMap {
        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            val index = item.optInt("i", -1)
            val short = item.optString("s")
            val full = item.optString("f").take(MAX_NAME_LENGTH)
            if (index >= 0 && full.isNotEmpty() && full != short) put(index, Entry(short, full))
        }
    }

    private fun preferences() = try {
        Applic.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }
}
