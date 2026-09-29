package tk.glucodata.ui.screens.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ColorPickerDialog(
    initialColorArgb: Int?,
    onColorSelected: (Int) -> Unit,
    onUseSystemColors: () -> Unit,
    onDismiss: () -> Unit
) {
    val systemColor = MaterialTheme.colorScheme.primary
    val initialArgb = initialColorArgb ?: systemColor.toArgb()
    val initialHsv = remember(initialArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialArgb, it) }
    }
    var hue by remember(initialHsv) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(initialHsv) { mutableStateOf(initialHsv[1]) }
    var brightness by remember(initialHsv) { mutableStateOf(initialHsv[2]) }
    val selectedColor = Color.hsv(hue, saturation, brightness)
    val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and selectedColor.toArgb())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_color_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RowLabel(label = hex, color = selectedColor)
                ColorSlider(
                    value = hue,
                    valueRange = 0f..360f,
                    onValueChange = { hue = (it * 10f).roundToInt() / 10f },
                    label = stringResource(R.string.settings_color_picker_hue),
                    valueText = "${hue.roundToInt()}°",
                    colors = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map {
                        Color.hsv(it, 1f, 1f)
                    },
                    contentDescription = stringResource(R.string.settings_color_picker_hue)
                )
                ColorSlider(
                    value = saturation,
                    valueRange = 0f..1f,
                    onValueChange = { saturation = (it * 100f).roundToInt() / 100f },
                    label = stringResource(R.string.settings_color_picker_saturation),
                    valueText = "${(saturation * 100f).roundToInt()}%",
                    colors = listOf(
                        Color.hsv(hue, 0f, brightness),
                        Color.hsv(hue, 1f, brightness)
                    ),
                    contentDescription = stringResource(R.string.settings_color_picker_saturation)
                )
                ColorSlider(
                    value = brightness,
                    valueRange = 0f..1f,
                    onValueChange = { brightness = (it * 100f).roundToInt() / 100f },
                    label = stringResource(R.string.settings_color_picker_brightness),
                    valueText = "${(brightness * 100f).roundToInt()}%",
                    colors = listOf(
                        Color.hsv(hue, saturation, 0f),
                        Color.hsv(hue, saturation, 1f)
                    ),
                    contentDescription = stringResource(R.string.settings_color_picker_brightness)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor.toArgb()) }) {
                Text(stringResource(R.string.dialog_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onUseSystemColors) {
                Text(stringResource(R.string.settings_color_picker_system))
            }
        }
    )
}

@Composable
private fun RowLabel(label: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun ColorSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    label: String,
    valueText: String,
    colors: List<Color>,
    contentDescription: String
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.horizontalGradient(colors), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Slider(
                value = value.coerceIn(valueRange),
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        this.contentDescription = contentDescription
                        stateDescription = valueText
                    }
            )
        }
    }
}
