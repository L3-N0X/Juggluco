package tk.glucodata.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import tk.glucodata.R

enum class SettingsDestination(
    val titleRes: Int,
    val descRes: Int,
    val categoryRes: Int,
    val icon: ImageVector
) {
    GLUCOSE_TARGETS(
        titleRes = R.string.settings_group_glucose_title,
        descRes = R.string.settings_group_glucose_desc,
        categoryRes = R.string.settings_cat_glucose,
        icon = Icons.Default.Straighten
    ),
    ALARMS(
        titleRes = R.string.settings_group_alarms_title,
        descRes = R.string.settings_group_alarms_desc,
        categoryRes = R.string.settings_cat_alarms,
        icon = Icons.Default.NotificationsActive
    ),
    DISPLAY(
        titleRes = R.string.settings_group_display_title,
        descRes = R.string.settings_group_display_desc,
        categoryRes = R.string.settings_cat_display,
        icon = Icons.Default.Palette
    ),
    VOICE(
        titleRes = R.string.settings_group_voice_title,
        descRes = R.string.settings_group_voice_desc,
        categoryRes = R.string.settings_voice_speech,
        icon = Icons.Default.RecordVoiceOver
    ),
    WATCH(
        titleRes = R.string.settings_group_watch_title,
        descRes = R.string.settings_group_watch_desc,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.Watch
    ),
    BROADCASTS(
        titleRes = R.string.settings_group_broadcasts_title,
        descRes = R.string.settings_group_broadcasts_desc,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.CloudSync
    ),
    MIRROR(
        titleRes = R.string.settings_group_mirror_title,
        descRes = R.string.settings_group_mirror_desc,
        categoryRes = R.string.settings_cat_dev_testing,
        icon = Icons.Default.Sync
    ),
    HARDWARE(
        titleRes = R.string.settings_group_hardware_title,
        descRes = R.string.settings_group_hardware_desc,
        categoryRes = R.string.settings_cat_hardware,
        icon = Icons.Default.Nfc
    ),
    DATA(
        titleRes = R.string.settings_group_data_title,
        descRes = R.string.settings_group_data_desc,
        categoryRes = R.string.settings_cat_data,
        icon = Icons.Default.FileUpload
    ),
    ABOUT(
        titleRes = R.string.settings_group_about_title,
        descRes = R.string.settings_group_about_desc,
        categoryRes = R.string.settings_cat_about,
        icon = Icons.Default.Info
    ),
    WEB_SERVER(
        titleRes = R.string.settings_title_web_server,
        descRes = R.string.settings_desc_web_server,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.Code
    ),
    LIBRE_VIEW(
        titleRes = R.string.settings_title_libre_view,
        descRes = R.string.settings_desc_libre_view,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.CloudSync
    ),
UPLOADER(
        titleRes = R.string.settings_title_uploader,
        descRes = R.string.settings_desc_uploader,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.CloudUpload
    ),
    LIBRE_VIEW_TREATMENTS(
        titleRes = R.string.loc_libreview_treatments,
        descRes = R.string.loc_libreview_treatments_desc,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.Assignment
    ),
    MIRROR_CONNECTION_EDIT(
        titleRes = R.string.settings_title_mirror_edit,
        descRes = R.string.settings_desc_mirror_edit,
        categoryRes = R.string.settings_cat_dev_testing,
        icon = Icons.Default.Sync
    ),
    TURN_SERVER(
        titleRes = R.string.settings_title_turn_server,
        descRes = R.string.settings_desc_turn_server,
        categoryRes = R.string.settings_cat_dev_testing,
        icon = Icons.Default.Sync
    ),
    FLOATING_WIDGET(
        titleRes = R.string.settings_floating_widget,
        descRes = R.string.settings_floating_widget_desc,
        categoryRes = R.string.settings_cat_display,
        icon = Icons.Default.Layers
    ),
    CALIBRATION(
        titleRes = R.string.calibration_title,
        descRes = R.string.calibration_enable_desc,
        categoryRes = R.string.settings_calibration_section,
        icon = Icons.Default.Tune
    ),
    GARMIN_STATUS(
        titleRes = R.string.loc_garmin_status_title,
        descRes = R.string.settings_garmin_desc,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.WatchLater
    ),
    GARMIN_CONFIG(
        titleRes = R.string.loc_garmin_config_title,
        descRes = R.string.loc_garmin_config_desc,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.Settings
    ),
    GARMIN_SHORTCUTS(
        titleRes = R.string.loc_garmin_shortcuts_title,
        descRes = R.string.loc_garmin_shortcuts_desc,
        categoryRes = R.string.settings_cat_integrations,
        icon = Icons.Default.Bolt
    ),
    LOGBOOK(
        titleRes = R.string.settings_group_logbook_title,
        descRes = R.string.settings_group_logbook_desc,
        categoryRes = R.string.settings_cat_glucose,
        icon = Icons.AutoMirrored.Filled.MenuBook
    ),
    INGREDIENTS(
        titleRes = R.string.meals_ingredients,
        descRes = R.string.settings_group_logbook_desc,
        categoryRes = R.string.settings_cat_glucose,
        icon = Icons.Default.Restaurant
    ),
    METERS(
        titleRes = R.string.meterlist,
        descRes = R.string.settings_group_meters_desc,
        categoryRes = R.string.settings_cat_hardware,
        icon = Icons.Default.Bloodtype
    )
}
