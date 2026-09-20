package tk.glucodata.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Floating
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun DisplaySettingsScreen(
    repository: GlucoseRepository,
    darkThemeOverride: Boolean? = null,
    onDarkThemeChanged: (Boolean?) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val displayConfig by repository.displayConfig.collectAsState()
    val context = LocalContext.current

    var isTouchable by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(0.85f) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_display_title),
        subtitle = stringResource(R.string.settings_cat_display),
        onNavigateBack = onNavigateBack
    ) {
        // THEME PREFERENCE
        SettingsCard(
            title = stringResource(R.string.settings_theme_pref),
            icon = Icons.Default.Palette,
            categorySubtitle = "APPEARANCE"
        ) {
            Text(
                text = stringResource(R.string.settings_theme_pref_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = darkThemeOverride == true,
                    onClick = { onDarkThemeChanged(true) },
                    label = { Text(stringResource(R.string.settings_theme_dark), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = darkThemeOverride == false,
                    onClick = { onDarkThemeChanged(false) },
                    label = { Text(stringResource(R.string.settings_theme_light), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = darkThemeOverride == null,
                    onClick = { onDarkThemeChanged(null) },
                    label = { Text(stringResource(R.string.settings_theme_system), fontSize = 12.sp) }
                )
            }
        }

        // FLOATING GLUCOSE WIDGET
        SettingsCard(
            title = stringResource(R.string.settings_floating_widget),
            icon = Icons.Default.Layers,
            categorySubtitle = "DESKTOP OVERLAY"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_floating_widget),
                subtitle = stringResource(R.string.settings_floating_widget_desc),
                checked = displayConfig.floatingGlucose,
                onCheckedChange = {
                    if (context is Activity) {
                        repository.setFloatingGlucose(it, context)
                    }
                }
            )

            if (displayConfig.floatingGlucose) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Touchable Overlay Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.dialog_touchable_overlay),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.dialog_touchable_overlay_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isTouchable,
                        onCheckedChange = {
                            isTouchable = it
                            try {
                                Floating.setTouchable(it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Opacity / Transparency Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_bg_opacity),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${(opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.2f..1.0f
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                SettingsInfoCard(
                    text = stringResource(R.string.dialog_floating_permission_note),
                    icon = Icons.Default.Info
                )
            }
        }

        // SYSTEM UI & GRAPH SETTINGS
        SettingsCard(
            title = "System UI & Layout",
            categorySubtitle = "VIEWPORT"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_status_bar),
                subtitle = stringResource(R.string.settings_status_bar_desc),
                checked = displayConfig.statusBarNotification,
                onCheckedChange = { repository.setStatusBarNotification(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_fullscreen),
                subtitle = stringResource(R.string.settings_fullscreen_desc),
                checked = displayConfig.systemUiFullscreen,
                onCheckedChange = {
                    repository.setSystemUiFullscreen(it, context as? Activity)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.minimalist_units),
                subtitle = stringResource(R.string.minimalist_units_desc),
                checked = displayConfig.minimalistUnits,
                onCheckedChange = { repository.setMinimalistUnits(it) }
            )
        }

        // CALIBRATION
        SettingsCard(
            title = stringResource(R.string.calibration_title),
            icon = Icons.Default.Tune,
            categorySubtitle = stringResource(R.string.settings_calibration_section)
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.calibration_enable),
                subtitle = stringResource(R.string.calibration_enable_desc),
                checked = displayConfig.calibrationEnabled,
                onCheckedChange = { repository.setCalibrationEnabled(it) }
            )

            if (displayConfig.calibrationEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.calibration_past),
                    subtitle = stringResource(R.string.calibration_past_desc),
                    checked = displayConfig.calibratePastReadings,
                    onCheckedChange = { repository.setCalibratePastReadings(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.calibration_all_values),
                    subtitle = stringResource(R.string.calibration_all_values_desc),
                    checked = displayConfig.calibrateAllValues,
                    onCheckedChange = { repository.setCalibrateAllValues(it) }
                )
            }
        }
    }
}
