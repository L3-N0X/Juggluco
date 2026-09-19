package tk.glucodata.ui.model

enum class GlucoseUnit(val label: String, val factor: Double) {
    MG_DL("mg/dL", 1.0),
    MMOL_L("mmol/L", 1.0 / 18.0182);

    fun format(valueMgDl: Float): String {
        return when (this) {
            MG_DL -> valueMgDl.toInt().toString()
            MMOL_L -> String.format(java.util.Locale.US, "%.1f", valueMgDl * factor)
        }
    }

    fun format(valueMgDl: Int): String = format(valueMgDl.toFloat())

    fun toMgDl(value: Float): Float {
        return when (this) {
            MG_DL -> value
            MMOL_L -> (value / factor).toFloat()
        }
    }

    fun formatRate(rateMgDlPerMin: Float): String {
        return when (this) {
            MG_DL -> String.format(java.util.Locale.US, "%.1f", rateMgDlPerMin)
            MMOL_L -> String.format(java.util.Locale.US, "%.2f", rateMgDlPerMin * factor)
        }
    }

    companion object {
        fun fromNative(unitCode: Int): GlucoseUnit {
            return if (unitCode == 1) MMOL_L else MG_DL
        }
    }
}
