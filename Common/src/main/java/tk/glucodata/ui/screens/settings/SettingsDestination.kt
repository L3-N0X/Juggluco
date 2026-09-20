package tk.glucodata.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Sync
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
    )
}
