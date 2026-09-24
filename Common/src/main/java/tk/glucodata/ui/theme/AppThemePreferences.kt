package tk.glucodata.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.glucodata.Applic

object AppThemePreferences {
    private const val PREFS = "app_theme"
    private const val KEY_CUSTOM_COLOR = "custom_color_argb"

    private val _customColorArgb = MutableStateFlow<Int?>(null)
    val customColorArgb: StateFlow<Int?> = _customColorArgb.asStateFlow()

    @Volatile
    private var loaded = false
    private lateinit var preferences: SharedPreferences

    fun ensureLoaded(context: Context = Applic.app) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _customColorArgb.value = if (preferences.contains(KEY_CUSTOM_COLOR)) {
                preferences.getInt(KEY_CUSTOM_COLOR, 0) or opaqueMask
            } else {
                null
            }
            loaded = true
        }
    }

    fun setCustomColor(colorArgb: Int) {
        ensureLoaded()
        val opaqueColor = colorArgb or opaqueMask
        _customColorArgb.value = opaqueColor
        preferences.edit().putInt(KEY_CUSTOM_COLOR, opaqueColor).apply()
    }

    fun useSystemColors() {
        ensureLoaded()
        _customColorArgb.value = null
        preferences.edit().remove(KEY_CUSTOM_COLOR).apply()
    }

    private const val opaqueMask = 0xFF000000.toInt()
}
