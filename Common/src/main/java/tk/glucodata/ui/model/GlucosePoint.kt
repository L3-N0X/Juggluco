package tk.glucodata.ui.model

data class GlucosePoint(
    val timestamp: Long,
    val valueMgDl: Float,
    val rate: Float = 0f,
    val isScan: Boolean = false,
    val isHistory: Boolean = false,
    val isCalibrated: Boolean = false,
    /** Which sensor produced this reading. Only meaningful for graph points; 0 when unknown. */
    val sensorIndex: Int = 0,
    val status: GlucoseStatus = GlucoseStatus.fromValue(valueMgDl, GlucoseRange.DEFAULT)
) {
    fun valueIn(unit: GlucoseUnit): Float = unit.toDisplay(valueMgDl)

    fun formatted(unit: GlucoseUnit): String = unit.format(valueMgDl)
}
