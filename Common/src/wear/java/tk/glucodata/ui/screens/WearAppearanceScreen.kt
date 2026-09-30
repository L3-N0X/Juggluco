package tk.glucodata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.R
import tk.glucodata.ui.theme.WearColorPreset
import tk.glucodata.ui.theme.WearThemePreferences
import tk.glucodata.ui.theme.wearAccentColor

/** Accent color choice for the watch UI. */
@Composable
fun WearAppearanceScreen() {
    val colorPreset by WearThemePreferences.colorPreset.collectAsState()
    val haptic = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }

    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text(stringResource(R.string.wear_ui_appearance)) } }

            item { ListSubHeader { Text(stringResource(R.string.wear_ui_color)) } }
            WearColorPreset.values().forEach { preset ->
                item {
                    RadioButton(
                        selected = colorPreset == preset,
                        onSelect = { tap { WearThemePreferences.setColorPreset(preset) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            wearAccentColor(preset, MaterialTheme.colorScheme.primary)
                                        )
                                )
                                Text(stringResource(preset.labelRes))
                            }
                        }
                    )
                }
            }
        }
    }
}
