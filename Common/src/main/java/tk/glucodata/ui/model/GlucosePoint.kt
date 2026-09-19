package tk.glucodata.ui.model

data class GlucosePoint(
    val timestamp: Long,
    val valueMgDl: Float,
    val rate: Float = 0f,
    val isScan: Boolean = false,
    val isHistory: Boolean = false,
    val isCalibrated: Boolean = false,
    val status: GlucoseStatus = GlucoseStatus.fromValue(valueMgDl)
) {
    fun valueIn(unit: GlucoseUnit): Float = when (unit) {
        GlucoseUnit.MG_DL -> valueMgDl
        GlucoseUnit.MMOL_L -> (valueMgDl * unit.factor).toFloat()
    }

    fun formatted(unit: GlucoseUnit): String = unit.format(valueMgDl)
}
