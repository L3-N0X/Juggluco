package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R
import java.util.Locale

enum class GlucoseUnit(@StringRes val labelRes: Int, val symbol: String, val factor: Double) {
    MG_DL(R.string.mgdL, "mg/dL", 1.0),
    MMOL_L(R.string.mmolL, "mmol/L", 1.0 / 18.0182);

    fun format(valueMgDl: Float, locale: Locale = Locale.getDefault()): String {
        return when (this) {
            MG_DL -> String.format(locale, "%.0f", valueMgDl)
            MMOL_L -> String.format(locale, "%.1f", valueMgDl * factor)
        }
    }

    fun format(valueMgDl: Int): String = format(valueMgDl.toFloat())

    fun toMgDl(value: Float): Float {
        return when (this) {
            MG_DL -> value
            MMOL_L -> (value / factor).toFloat()
        }
    }

    fun formatRate(rateMgDlPerMin: Float, locale: Locale = Locale.getDefault()): String {
        return when (this) {
            MG_DL -> String.format(locale, "%.1f", rateMgDlPerMin)
            MMOL_L -> String.format(locale, "%.2f", rateMgDlPerMin * factor)
        }
    }

    companion object {
        fun fromNative(unitCode: Int): GlucoseUnit {
            return if (unitCode == 1) MMOL_L else MG_DL
        }
    }
}
