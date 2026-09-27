package tk.glucodata.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.R
import tk.glucodata.ui.components.WearQuickLogDial
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogType
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val CategoryPickerHeight = 42.dp

private val CategoryLabelSizes = listOf(12.sp, 11.sp, 10.sp, 9.sp)

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

    var selectedType by remember { mutableStateOf(LogType.CARBS) }
    val spec = remember(selectedType, unit) { specFor(selectedType, unit) }
    var value by remember(spec) { mutableFloatStateOf(spec.initial) }

    fun adjust(delta: Float) {
        val steps = ((value + delta - spec.min) / spec.smallStep).roundToInt()
        value = (spec.min + steps * spec.smallStep).coerceIn(spec.min, spec.max)
    }

    val (displayText, labelText) = when (selectedType) {
        LogType.CARBS -> "${value.roundToInt()} g" to stringResource(R.string.log_short_carbs)
        LogType.RAPID_INSULIN -> String.format(java.util.Locale.getDefault(), "%.1f U", value) to stringResource(R.string.log_short_bolus)
        LogType.BASAL_INSULIN -> String.format(java.util.Locale.getDefault(), "%.1f U", value) to stringResource(R.string.log_short_basal)
        LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
            String.format(java.util.Locale.getDefault(), "%.1f", value) to stringResource(unit.labelRes)
        } else {
            "${value.roundToInt()}" to stringResource(unit.labelRes)
        }
        else -> "$value" to ""
    }

    val haptic = LocalHapticFeedback.current
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )

    val carbsLabel = stringResource(R.string.log_pick_carbs)
    val bolusLabel = stringResource(R.string.log_pick_bolus)
    val basalLabel = stringResource(R.string.log_pick_basal)
    val bgLabel = stringResource(R.string.log_pick_bg)
    val categories = remember(carbsLabel, bolusLabel, basalLabel, bgLabel) {
        listOf(
            QuickLogCategory(carbsLabel, LogType.CARBS),
            QuickLogCategory(bolusLabel, LogType.RAPID_INSULIN),
            QuickLogCategory(basalLabel, LogType.BASAL_INSULIN),
            QuickLogCategory(bgLabel, LogType.BLOOD_GLUCOSE)
        )
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                SemicircleCategoryPicker(
                    categories = categories,
                    selected = selectedType,
                    onSelect = { type ->
                        selectedType = type
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }

            // Circular rotary dial: crown + spin-the-ring to adjust
            item {
                WearQuickLogDial(
                    value = value,
                    range = spec.min..spec.max,
                    step = spec.smallStep,
                    onValueChange = { value = it },
                    displayText = displayText,
                    labelText = labelText,
                    contentDescription = when (selectedType) {
                        LogType.CARBS -> "Carbohydrates in grams"
                        LogType.RAPID_INSULIN -> "Rapid insulin in units"
                        LogType.BASAL_INSULIN -> "Basal insulin in units"
                        LogType.BLOOD_GLUCOSE -> "Blood glucose"
                        else -> "Value"
                    }
                )
            }

            item {
                Text(
                    text = "Turn crown or spin ring",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Stepper buttons with clear font size
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactButton(
                        onClick = {
                            adjust(-spec.largeStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "-${formatStep(spec.largeStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            adjust(-spec.smallStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "-${formatStep(spec.smallStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            adjust(spec.smallStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "+${formatStep(spec.smallStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            adjust(spec.largeStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "+${formatStep(spec.largeStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Save Action (standard Wear M3 Button)
            item {
                Button(
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
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text("Save")
                    }
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
    onSelect: (LogType) -> Unit
) {
    if (categories.isEmpty()) return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val baseStyle = MaterialTheme.typography.labelMedium

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(CategoryPickerHeight)
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

        // Rail sits lowest at the outer labels and arcs up towards the middle ones.
        val railLowestY = heightPx - textBottomPadPx - tallestTextPx - dotGapPx -
            dotRadiusPx - railStrokePx / 2f
        val amplitudePx = with(density) { 12.dp.toPx() }
            .coerceAtMost((railLowestY - railStrokePx).coerceAtLeast(0f))

        fun railYAt(x: Float): Float {
            val t = ((x - railStartX) / railSpanX).coerceIn(0f, 1f)
            return railLowestY - amplitudePx * sin(PI * t).toFloat()
        }

        val anchorX = { index: Int -> slotWidthPx * (index + 0.5f) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path()
            val steps = 40
            for (step in 0..steps) {
                val t = step / steps.toFloat()
                val x = railStartX + railSpanX * t
                val y = railLowestY - amplitudePx * sin(PI * t).toFloat()
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
                        color = colors.primary.copy(alpha = 0.28f),
                        radius = dotRadiusPx * 2.1f,
                        center = center
                    )
                }
                drawCircle(
                    color = if (label.category.type == selected) colors.primary else colors.outline,
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
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
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
