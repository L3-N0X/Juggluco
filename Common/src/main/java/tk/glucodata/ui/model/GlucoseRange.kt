package tk.glucodata.ui.model

import androidx.compose.runtime.Immutable

/**
 * The four cut points that split a glucose value into the five bands the app reports: very low,
 * low, in range, high and very high.
 *
 * Everything that classifies, colours, aggregates or graphs a reading goes through here, so a change
 * in settings is picked up by the graphs, statistics, widgets and watch alike instead of silently
 * falling back to the consensus defaults. Values are always stored in mg/dL; conversion for display
 * happens at the edge via [GlucoseUnit].
 *
 * The four edges are kept ordered and [MIN_GAP] apart by [withLevel] and [normalized], so no
 * combination of writes can produce an empty or inverted band.
 */
@Immutable
data class GlucoseRange(
    val veryLowMgDl: Float = DEFAULT_VERY_LOW,
    val lowMgDl: Float = DEFAULT_LOW,
    val highMgDl: Float = DEFAULT_HIGH,
    val veryHighMgDl: Float = DEFAULT_VERY_HIGH
) {
    fun statusOf(valueMgDl: Float): GlucoseStatus = when {
        valueMgDl < veryLowMgDl -> GlucoseStatus.VERY_LOW
        valueMgDl < lowMgDl -> GlucoseStatus.LOW
        valueMgDl > veryHighMgDl -> GlucoseStatus.VERY_HIGH
        valueMgDl > highMgDl -> GlucoseStatus.HIGH
        else -> GlucoseStatus.IN_RANGE
    }

    fun valueFor(level: RangeLevel): Float = when (level) {
        RangeLevel.VERY_LOW -> veryLowMgDl
        RangeLevel.LOW -> lowMgDl
        RangeLevel.HIGH -> highMgDl
        RangeLevel.VERY_HIGH -> veryHighMgDl
    }

    /**
     * Window a control for [level] may move through. Normally this is the absolute window for that
     * edge narrowed by its immediate neighbour. When the neighbours sit closer together than
     * [MIN_SPAN] the window widens around the current value instead, so the control stays usable;
     * writing a value from it then nudges the neighbour rather than being silently discarded.
     */
    fun sliderBoundsFor(level: RangeLevel): ClosedFloatingPointRange<Float> {
        val lower = when (level) {
            RangeLevel.VERY_LOW -> MIN_MGDL
            RangeLevel.LOW -> maxOf(floorFor(level), veryLowMgDl + MIN_GAP)
            RangeLevel.HIGH -> maxOf(floorFor(level), lowMgDl + MIN_GAP)
            RangeLevel.VERY_HIGH -> maxOf(floorFor(level), highMgDl + MIN_GAP)
        }
        val upper = when (level) {
            RangeLevel.VERY_HIGH -> MAX_MGDL
            RangeLevel.HIGH -> minOf(ceilingFor(level), veryHighMgDl - MIN_GAP)
            RangeLevel.LOW -> minOf(ceilingFor(level), highMgDl - MIN_GAP)
            RangeLevel.VERY_LOW -> minOf(ceilingFor(level), lowMgDl - MIN_GAP)
        }
        if (upper - lower >= MIN_SPAN) return lower..upper
        val centre = valueFor(level)
        return (centre - MIN_SPAN / 2f)..(centre + MIN_SPAN / 2f)
    }

    /**
     * Returns a copy with [level] at [valueMgDl], pushing whichever neighbours it would cross and
     * keeping the four cut points ordered and [MIN_GAP] apart. This is the single write path: the
     * settings screens use it, and [tk.glucodata.ui.data.GlucoseRepository] runs it again on
     * whatever comes back from storage.
     */
    fun withLevel(level: RangeLevel, valueMgDl: Float): GlucoseRange = ordered(
        if (level == RangeLevel.VERY_LOW) valueMgDl else veryLowMgDl,
        if (level == RangeLevel.LOW) valueMgDl else lowMgDl,
        if (level == RangeLevel.HIGH) valueMgDl else highMgDl,
        if (level == RangeLevel.VERY_HIGH) valueMgDl else veryHighMgDl
    )

    /** Repairs ordering, spacing and the absolute window, e.g. for values read back from storage. */
    fun normalized(): GlucoseRange =
        ordered(veryLowMgDl, lowMgDl, highMgDl, veryHighMgDl)

    companion object {        const val DEFAULT_VERY_LOW = 54f
        const val DEFAULT_LOW = 70f
        const val DEFAULT_HIGH = 180f
        const val DEFAULT_VERY_HIGH = 250f

        /** Smallest gap kept between two neighbouring cut points, in mg/dL. */
        const val MIN_GAP = 5f

        /** Absolute bounds the outermost cut points may take, in mg/dL. */
        const val MIN_MGDL = 30f
        const val MAX_MGDL = 400f

        /** Narrowest window a control for one cut point is ever offered. */
        const val MIN_SPAN = 20f

        val DEFAULT = GlucoseRange()

        /** Leaves each interior edge room for both of its neighbours. */
        private fun floorFor(level: RangeLevel): Float = MIN_MGDL + MIN_GAP * level.ordinal

        private fun ceilingFor(level: RangeLevel): Float = MAX_MGDL - MIN_GAP * (RangeLevel.entries.lastIndex - level.ordinal)

        /**
         * Sweeps the four edges apart and then back inside the absolute window until neither can
         * move, which is what makes the result canonical: the same four numbers always normalise to
         * the same range, whatever order they were assigned in.
         */
        private fun ordered(veryLow: Float, low: Float, high: Float, veryHigh: Float): GlucoseRange {
            var a = veryLow.coerceIn(MIN_MGDL, MAX_MGDL)
            var b = low
            var c = high
            var d = veryHigh
            repeat(2) {
                if (b < a + MIN_GAP) b = a + MIN_GAP
                if (c < b + MIN_GAP) c = b + MIN_GAP
                if (d < c + MIN_GAP) d = c + MIN_GAP
                if (d > MAX_MGDL) d = MAX_MGDL
                if (c > d - MIN_GAP) c = d - MIN_GAP
                if (b > c - MIN_GAP) b = c - MIN_GAP
                if (a > b - MIN_GAP) a = b - MIN_GAP
            }
            return GlucoseRange(a, b, c, d)
        }
    }
}

enum class RangeLevel { VERY_LOW, LOW, HIGH, VERY_HIGH }
