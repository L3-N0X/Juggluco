package tk.glucodata.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun DisplaySettingsScreen(
    repository: GlucoseRepository,
    darkThemeOverride: Boolean? = null,
    onDarkThemeChanged: (Boolean?) -> Unit = {},
    onOpenFloatingWidgetConfig: () -> Unit = {},
    onOpenCalibrationConfig: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val displayConfig by repository.displayConfig.collectAsState()
    val context = LocalContext.current

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_display_title),
        onNavigateBack = onNavigateBack
    ) {
        // THEME PREFERENCE
        SettingsSection(title = "Appearance") {
            SettingsSegmentedRow(
                title = stringResource(R.string.settings_theme_pref),
                subtitle = stringResource(R.string.settings_theme_pref_desc),
                icon = Icons.Default.Palette
            ) {
                @OptIn(ExperimentalMaterial3Api::class)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    SegmentedButton(
                        selected = darkThemeOverride == null,
                        onClick = { onDarkThemeChanged(null) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.BrightnessAuto,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.settings_theme_system),
                                maxLines = 1
                            )
                        }
                    )
                    SegmentedButton(
                        selected = darkThemeOverride == false,
                        onClick = { onDarkThemeChanged(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.settings_theme_light),
                                maxLines = 1
                            )
                        }
                    )
                    SegmentedButton(
                        selected = darkThemeOverride == true,
                        onClick = { onDarkThemeChanged(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.settings_theme_dark),
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }

        // SYSTEM UI & LAYOUT
        SettingsSection(title = "System UI & curve layout") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_status_bar),
                subtitle = stringResource(R.string.settings_status_bar_desc),
                icon = Icons.Default.Notifications,
                checked = displayConfig.statusBarNotification,
                onCheckedChange = { repository.setStatusBarNotification(it) }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_fullscreen),
                subtitle = stringResource(R.string.settings_fullscreen_desc),
                icon = Icons.Default.Fullscreen,
                checked = displayConfig.systemUiFullscreen,
                onCheckedChange = {
                    repository.setSystemUiFullscreen(it, context as? Activity)
                }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.minimalist_units),
                subtitle = stringResource(R.string.minimalist_units_desc),
                icon = Icons.AutoMirrored.Filled.ShortText,
                checked = displayConfig.minimalistUnits,
                onCheckedChange = { repository.setMinimalistUnits(it) }
            )
        }

        // ADVANCED TOOLS
        SettingsSection(title = "Advanced tools") {
            SettingsNavRow(
                title = stringResource(R.string.settings_floating_widget),
                subtitle = stringResource(R.string.settings_floating_widget_desc),
                icon = Icons.Default.Layers,
                checked = displayConfig.floatingGlucose,
                onCheckedChange = {
                    if (context is Activity) {
                        repository.setFloatingGlucose(it, context)
                    }
                },
                onClick = onOpenFloatingWidgetConfig
            )

            SettingsDivider()

            SettingsNavRow(
                title = stringResource(R.string.calibration_title),
                subtitle = stringResource(R.string.calibration_enable_desc),
                icon = Icons.Default.Tune,
                checked = displayConfig.calibrationEnabled,
                onCheckedChange = { repository.setCalibrationEnabled(it) },
                onClick = onOpenCalibrationConfig
            )
        }
    }
}
