package tk.glucodata.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import tk.glucodata.R
import tk.glucodata.ui.components.WearQuickLogDial
import tk.glucodata.ui.components.shortDisplayName
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.theme.LocalLogbookColors
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val CategoryPickerHeight = 60.dp

// Leaves the bottom of the ring open for the Save edge button.
private const val DialSweepAngle = 230f

// Extra-small edge button height plus a small gap above it.
private val EdgeButtonReserve = 50.dp

// Short label names are the user's own (up to 11 bytes), so the smallest step is a last resort.
private val CategoryLabelSizes = listOf(12.sp, 11.sp, 10.sp, 9.sp, 8.sp)

private data class QuickLogCategory(val label: String, val type: LogType)

private data class QuickLogSpec(
    val min: Float,
    val max: Float,
    val smallStep: Float,
    val largeStep: Float,
    val initial: Float
)

private fun specFor(type: LogType, unit: GlucoseUnit): QuickLogSpec {
    return when (type) {
        LogType.CARBS -> QuickLogSpec(min = 0f, max = 150f, smallStep = 1f, largeStep = 5f, initial = 20f)
        LogType.RAPID_INSULIN -> QuickLogSpec(min = 0f, max = 30f, smallStep = 0.5f, largeStep = 1f, initial = 2f)
        LogType.BASAL_INSULIN -> QuickLogSpec(min = 0f, max = 60f, smallStep = 0.5f, largeStep = 1f, initial = 10f)
        LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
            QuickLogSpec(min = 1.1f, max = 22.2f, smallStep = 0.1f, largeStep = 1f, initial = 5.5f)
        } else {
            QuickLogSpec(min = 20f, max = 400f, smallStep = 1f, largeStep = 10f, initial = 100f)
        }
        else -> QuickLogSpec(min = 0f, max = 100f, smallStep = 1f, largeStep = 5f, initial = 0f)
    }
}

@Composable
fun WearQuickLogScreen(
    repository: GlucoseRepository,
    onSaved: () -> Unit
) {
    val unit by repository.unit.collectAsState()
    val logs by repository.logs.collectAsState()
    val labels by repository.labelConfig.collectAsState()

    var selectedType by remember { mutableStateOf(LogType.CARBS) }
    val spec = remember(selectedType, unit) { specFor(selectedType, unit) }
    // Starts from the amount last logged for this type, which is far more likely to be close
    // than any fixed default; the spec's default only covers a fresh logbook.
    var value by remember(spec) {
        val last = logs.firstOrNull { it.type == selectedType }?.value
            ?.let { if (selectedType == LogType.BLOOD_GLUCOSE) unit.toDisplay(it) else it }
        val start = last?.let {
            (spec.min + ((it - spec.min) / spec.smallStep).roundToInt() * spec.smallStep).coerceIn(spec.min, spec.max)
        }
        mutableFloatStateOf(start?.takeIf { it > spec.min } ?: spec.initial)
    }
    val typeColors = LocalLogbookColors.current.forType(selectedType)

    fun adjust(delta: Float) {
        val steps = ((value + delta - spec.min) / spec.smallStep).roundToInt()
        value = (spec.min + steps * spec.smallStep).coerceIn(spec.min, spec.max)
    }

    // The labels' short names are the ones made to fit a watch; the type names only stand in
    // until the labels are read.
    val (displayText, labelText) = when (selectedType) {
        LogType.CARBS -> stringResource(R.string.log_value_carbs, "${value.roundToInt()}") to
            labels.shortDisplayName(LogType.CARBS)
        LogType.RAPID_INSULIN ->
            stringResource(R.string.log_value_insulin, String.format(java.util.Locale.getDefault(), "%.1f", value)) to
                labels.shortDisplayName(LogType.RAPID_INSULIN)
        LogType.BASAL_INSULIN ->
            stringResource(R.string.log_value_insulin, String.format(java.util.Locale.getDefault(), "%.1f", value)) to
                labels.shortDisplayName(LogType.BASAL_INSULIN)
        LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
            String.format(java.util.Locale.getDefault(), "%.1f", value) to stringResource(unit.labelRes)
        } else {
            "${value.roundToInt()}" to stringResource(unit.labelRes)
        }
        else -> "$value" to ""
    }

    val haptic = LocalHapticFeedback.current

    val carbsLabel = labels.shortDisplayName(LogType.CARBS, R.string.log_pick_carbs)
    val bolusLabel = labels.shortDisplayName(LogType.RAPID_INSULIN, R.string.log_pick_bolus)
    val basalLabel = labels.shortDisplayName(LogType.BASAL_INSULIN, R.string.log_pick_basal)
    val bgLabel = labels.shortDisplayName(LogType.BLOOD_GLUCOSE, R.string.log_pick_bg)
    val categories = remember(carbsLabel, bolusLabel, basalLabel, bgLabel) {
        listOf(
            QuickLogCategory(carbsLabel, LogType.CARBS),
            QuickLogCategory(bolusLabel, LogType.RAPID_INSULIN),
            QuickLogCategory(basalLabel, LogType.BASAL_INSULIN),
            QuickLogCategory(bgLabel, LogType.BLOOD_GLUCOSE)
        )
    }

    // Everything fits on one screen: the dial runs along the edge, the picker, value and
    // steppers sit inside it, and Save is an edge button in the ring's bottom gap. A scrolling
    // list does not work here because the dial owns both the crown and drags on the ring.
    ScreenScaffold(timeText = {}) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(maxWidth, maxHeight)

            WearQuickLogDial(
                value = value,
                range = spec.min..spec.max,
                step = spec.smallStep,
                onValueChange = { value = it },
                stateDescription = displayText,
                contentDescription = labels.nameFor(selectedType).ifBlank { stringResource(selectedType.labelRes) },
                accentColor = typeColors.primary,
                sweepAngle = DialSweepAngle,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = diameter * 0.1f, bottom = EdgeButtonReserve),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SemicircleCategoryPicker(
                        categories = categories,
                        selected = selectedType,
                        onSelect = { type ->
                            selectedType = type
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.width(diameter * 0.7f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = displayText,
                            fontSize = if (diameter < 200.dp) 24.sp else 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = labelText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    // Narrower than the picker: the row sits lower, where the ring closes in.
                    Row(
                        modifier = Modifier.width(diameter * 0.66f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf(-spec.largeStep, -spec.smallStep, spec.smallStep, spec.largeStep).forEach { delta ->
                            CompactButton(
                                onClick = {
                                    adjust(delta)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Text(
                                    text = (if (delta < 0f) "-" else "+") + formatStep(kotlin.math.abs(delta)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            EdgeButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val rawValue = if (selectedType == LogType.BLOOD_GLUCOSE && unit == GlucoseUnit.MMOL_L) {
                        unit.toMgDl(value)
                    } else {
                        value
                    }
                    repository.addLogEntry(selectedType, rawValue, "")
                    onSaved()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                buttonSize = EdgeButtonSize.ExtraSmall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = typeColors.primary,
                    contentColor = typeColors.container,
                    iconColor = typeColors.container
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.save)
                )
            }
        }
    }
}

private fun formatStep(step: Float): String {
    return if (step < 1f) {
        String.format(java.util.Locale.US, "%.1f", step).trimEnd('0').trimEnd('.').let {
            if (it.startsWith("0")) it.substring(1) else it
        }.let { if (it.startsWith(".")) "0$it" else it }
    } else {
        step.roundToInt().toString()
    }
}

/**
 * Log category selector laid out along a semicircular rail.
 *
 * All labels sit on one arc, so the whole selector stays a single compact band above the
 * dial instead of a wrapping multi-row pill grid. Each label gets an evenly sized slot on
 * the arc and shrinks (down to [CategoryLabelSizes] last) if it does not fit, which keeps
 * every label on one line in any locale.
 */
@Composable
private fun SemicircleCategoryPicker(
    categories: List<QuickLogCategory>,
    selected: LogType,
    onSelect: (LogType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val selectedColor = LocalLogbookColors.current.forType(selected).primary
    val baseStyle = MaterialTheme.typography.labelMedium

    BoxWithConstraints(
        modifier = modifier.height(CategoryPickerHeight)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { CategoryPickerHeight.toPx() }
        val slotWidthPx = widthPx / categories.size
        val maxLabelWidthPx = slotWidthPx - with(density) { 4.dp.toPx() }

        // Measured with the bold weight so switching the selection never reflows the arc.
        val labels = remember(categories, maxLabelWidthPx, baseStyle) {
            categories.map { category ->
                var chosenSize = CategoryLabelSizes.last()
                var measured = measurer.measure(
                    text = AnnotatedString(category.label),
                    style = labelStyle(baseStyle, chosenSize)
                )
                for (fontSize in CategoryLabelSizes) {
                    val candidate = measurer.measure(
                        text = AnnotatedString(category.label),
                        style = labelStyle(baseStyle, fontSize)
                    )
                    chosenSize = fontSize
                    measured = candidate
                    if (candidate.size.width <= maxLabelWidthPx) break
                }
                LabelLayout(category, chosenSize, measured.size)
            }
        }

        val dotRadiusPx = with(density) { 3.dp.toPx() }
        val dotGapPx = with(density) { 6.dp.toPx() }
        val railStrokePx = with(density) { 2.dp.toPx() }
        val textBottomPadPx = with(density) { 2.dp.toPx() }
        val tallestTextPx = labels.maxOf { it.size.height }
        val railStartX = (slotWidthPx / 2f) - with(density) { 8.dp.toPx() }
        val railEndX = widthPx - railStartX
        val railSpanX = (railEndX - railStartX).coerceAtLeast(1f)

        // Rail is a true circular arc: lowest at the outer labels, rising to the middle ones,
        // so it echoes the round watch face instead of reading as a flat wave.
        val railLowestY = heightPx - textBottomPadPx - tallestTextPx - dotGapPx -
            dotRadiusPx - railStrokePx / 2f
        val sagittaPx = with(density) { 28.dp.toPx() }
            .coerceAtMost((railLowestY - railStrokePx).coerceAtLeast(1f))
        val halfChordPx = railSpanX / 2f
        val railRadiusPx = (halfChordPx * halfChordPx + sagittaPx * sagittaPx) / (2f * sagittaPx)
        val railCenterX = railStartX + halfChordPx
        val railCenterY = railLowestY - sagittaPx + railRadiusPx

        fun railYAt(x: Float): Float {
            val dx = (x - railCenterX).coerceIn(-halfChordPx, halfChordPx)
            return railCenterY - sqrt(railRadiusPx * railRadiusPx - dx * dx)
        }

        val anchorX = { index: Int -> slotWidthPx * (index + 0.5f) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path()
            val steps = 40
            for (step in 0..steps) {
                val t = step / steps.toFloat()
                val x = railStartX + railSpanX * t
                val y = railYAt(x)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = colors.outlineVariant,
                style = Stroke(width = railStrokePx, cap = StrokeCap.Round)
            )

            labels.forEachIndexed { index, label ->
                val center = Offset(anchorX(index), railYAt(anchorX(index)))
                if (label.category.type == selected) {
                    drawCircle(
                        color = selectedColor.copy(alpha = 0.28f),
                        radius = dotRadiusPx * 2.1f,
                        center = center
                    )
                }
                drawCircle(
                    color = if (label.category.type == selected) selectedColor else colors.outline,
                    radius = dotRadiusPx,
                    center = center
                )
            }
        }

        labels.forEachIndexed { index, label ->
            val isSelected = label.category.type == selected
            val labelTopPx = railYAt(anchorX(index)) + dotRadiusPx + dotGapPx
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((slotWidthPx * index).roundToInt(), labelTopPx.roundToInt())
                    }
                    .size(
                        width = with(density) { slotWidthPx.toDp() },
                        height = with(density) { (heightPx - labelTopPx).toDp() }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(label.category.type) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = label.category.label,
                    fontSize = label.fontSize,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) selectedColor else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class LabelLayout(
    val category: QuickLogCategory,
    val fontSize: TextUnit,
    val size: IntSize
)

private fun labelStyle(base: TextStyle, fontSize: TextUnit): TextStyle = base.copy(
    fontSize = fontSize,
    fontWeight = FontWeight.Bold
)
