package tk.glucodata.ui.screens.settings

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.util

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
)

object AppLanguageManager {
    private val rawLanguages = listOf(
        AppLanguage(code = "ar", nativeName = "العربية", englishName = "Arabic"),
        AppLanguage(code = "be", nativeName = "Беларуская", englishName = "Belarusian"),
        AppLanguage(code = "zh", nativeName = "中文", englishName = "Chinese"),
        AppLanguage(code = "nl", nativeName = "Nederlands", englishName = "Dutch"),
        AppLanguage(code = "en", nativeName = "English", englishName = "English"),
        AppLanguage(code = "fr", nativeName = "Français", englishName = "French"),
        AppLanguage(code = "de", nativeName = "Deutsch", englishName = "German"),
        AppLanguage(code = "hi", nativeName = "हिन्दी", englishName = "Hindi"),
        AppLanguage(code = "it", nativeName = "Italiano", englishName = "Italian"),
        AppLanguage(code = "ja", nativeName = "日本語", englishName = "Japanese"),
        AppLanguage(code = "ko", nativeName = "한국어", englishName = "Korean"),
        AppLanguage(code = "pl", nativeName = "Polski", englishName = "Polish"),
        AppLanguage(code = "pt", nativeName = "Português", englishName = "Portuguese"),
        AppLanguage(code = "ru", nativeName = "Русский", englishName = "Russian"),
        AppLanguage(code = "es", nativeName = "Español", englishName = "Spanish"),
        AppLanguage(code = "sv", nativeName = "Svenska", englishName = "Swedish"),
        AppLanguage(code = "tr", nativeName = "Türkçe", englishName = "Turkish"),
        AppLanguage(code = "uk", nativeName = "Українська", englishName = "Ukrainian"),
        AppLanguage(code = "uz", nativeName = "Oʻzbekcha", englishName = "Uzbek")
    ).sortedBy { it.englishName }

    fun getSupportedLanguages(context: Context): List<AppLanguage> {
        val systemDefault = AppLanguage(
            code = "",
            nativeName = context.getString(R.string.settings_language_system_default),
            englishName = context.getString(R.string.settings_language_follow_system)
        )
        return listOf(systemDefault) + rawLanguages
    }

    fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        val locale = locales.get(0) ?: return ""
        val lang = locale.language
        return if (rawLanguages.any { it.code.equals(lang, ignoreCase = true) }) {
            lang
        } else {
            ""
        }
    }

    fun getLanguageDisplayName(code: String, context: Context): String {
        if (code.isEmpty()) {
            val systemLocale = util.getlocale()
            val match = rawLanguages.find {
                it.code.equals(systemLocale.language, ignoreCase = true)
            }
            val systemName = match?.nativeName ?: systemLocale.displayLanguage
            val defaultStr = context.getString(R.string.settings_language_system_default)
            return if (!systemName.isNullOrEmpty()) context.getString(R.string.loc_system_language_with_name, defaultStr, systemName) else defaultStr
        }
        val lang = rawLanguages.find { it.code.equals(code, ignoreCase = true) }
        return if (lang != null) {
            if (lang.nativeName.equals(lang.englishName, ignoreCase = true)) {
                lang.nativeName
            } else {
                context.getString(R.string.loc_language_with_english, lang.nativeName, lang.englishName)
            }
        } else {
            code
        }
    }

    fun getLanguageBadge(code: String): String {
        if (code.isEmpty()) return Applic.getContext().getString(R.string.loc_auto)
        return code.uppercase(Locale.US)
    }

    fun setLanguage(code: String) {
        val newLocale = if (code.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
        AppCompatDelegate.setApplicationLocales(newLocale)
        try {
            val lang = if (code.isEmpty()) {
                util.getlocale().language
            } else {
                code
            }
            if (Applic.Nativesloaded) {
                Natives.setlocale(lang, Build.VERSION.SDK_INT)
                Applic.curlang = lang
            }
        } catch (_: Throwable) {}
    }
}
