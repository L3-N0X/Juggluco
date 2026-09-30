package tk.glucodata.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.glucodata.Applic
import tk.glucodata.R

enum class WearColorPreset(@StringRes val labelRes: Int) {
    WATCH_DEFAULT(R.string.wear_color_watch_default),
    BLUE(R.string.wear_color_blue),
    TEAL(R.string.wear_color_teal),
    PURPLE(R.string.wear_color_purple),
    ORANGE(R.string.wear_color_orange),
    GREEN(R.string.wear_color_green),
    PINK(R.string.wear_color_pink)
}

object WearThemePreferences {
    private const val PREFS = "wear_theme"
    private const val KEY_COLOR_PRESET = "color_preset"

    private val _colorPreset = MutableStateFlow(WearColorPreset.WATCH_DEFAULT)
    val colorPreset: StateFlow<WearColorPreset> = _colorPreset.asStateFlow()

    @Volatile
    private var loaded = false
    private lateinit var preferences: SharedPreferences

    fun ensureLoaded(context: Context = Applic.app) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = preferences.getString(KEY_COLOR_PRESET, null)
            _colorPreset.value = stored?.let { value ->
                runCatching { WearColorPreset.valueOf(value) }.getOrNull()
            } ?: WearColorPreset.WATCH_DEFAULT
            loaded = true
        }
    }

    fun setColorPreset(preset: WearColorPreset) {
        ensureLoaded()
        _colorPreset.value = preset
        preferences.edit().putString(KEY_COLOR_PRESET, preset.name).apply()
    }
}
