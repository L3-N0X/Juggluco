package tk.glucodata.ui.screens.settings

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tk.glucodata.Floating
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun FloatingWidgetSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val displayConfig by repository.displayConfig.collectAsState()
    val context = LocalContext.current

    var isTouchable by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(0.85f) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_floating_widget),
        onNavigateBack = onNavigateBack
    ) {
        // OVERLAY CONTROL
        SettingsSection(title = "Overlay window") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_floating_widget),
                subtitle = stringResource(R.string.settings_floating_widget_desc),
                icon = Icons.Default.Layers,
                checked = displayConfig.floatingGlucose,
                onCheckedChange = {
                    if (context is Activity) {
                        repository.setFloatingGlucose(it, context)
                    }
                }
            )
        }

        if (displayConfig.floatingGlucose) {
            SettingsSection(title = "Appearance & behavior") {
                SettingsSwitchRow(
                    title = stringResource(R.string.dialog_touchable_overlay),
                    subtitle = stringResource(R.string.dialog_touchable_overlay_desc),
                    icon = Icons.Default.TouchApp,
                    checked = isTouchable,
                    onCheckedChange = {
                        isTouchable = it
                        try {
                            Floating.setTouchable(it)
                        } catch (_: Throwable) {}
                    }
                )

                SettingsDivider()

                SettingsSliderRow(
                    title = stringResource(R.string.dialog_bg_opacity),
                    valueText = "${(opacity * 100).toInt()}%",
                    icon = Icons.Default.Opacity,
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.2f..1.0f
                )
            }
        }

        SettingsInfoCard(
            text = stringResource(R.string.dialog_floating_permission_note),
            icon = Icons.Default.Info
        )
    }
}
