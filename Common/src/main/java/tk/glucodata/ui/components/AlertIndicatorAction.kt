package tk.glucodata.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertStatus
import tk.glucodata.alerts.AlertStore
import tk.glucodata.alerts.rememberAlertIndicatorState
import tk.glucodata.ui.screens.settings.formatClock
import tk.glucodata.ui.screens.settings.formatDuration

/**
 * Top app bar action showing whether alerts are on, snoozed or off. While an alert
 * rings it shows a swinging bell and a tap dismisses the alert on every device;
 * otherwise a tap opens quick snooze controls. With [showWhenIdle] off it only
 * appears while an alert rings.
 */
@Composable
fun AlertIndicatorAction(onOpenAlertSettings: () -> Unit, showWhenIdle: Boolean = true) {
    val state = rememberAlertIndicatorState()
    var menuOpen by remember { mutableStateOf(false) }
    if (!showWhenIdle && state.status != AlertStatus.RINGING) return

    Box {
        when (state.status) {
            AlertStatus.RINGING -> {
                val swing by rememberInfiniteTransition(label = "bell").animateFloat(
                    initialValue = -18f,
                    targetValue = 18f,
                    animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
                    label = "bellSwing"
                )
                IconButton(onClick = { AlertPlayer.dismiss() }) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = stringResource(
                            R.string.alert_dismiss_named,
                            state.ringingName ?: stringResource(R.string.alarm)
                        ),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.graphicsLayer { rotationZ = swing }
                    )
                }
            }
            AlertStatus.ACTIVE -> IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.alerts_on),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AlertStatus.SNOOZED -> IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.NotificationsPaused,
                    contentDescription = stringResource(R.string.alerts_snoozed),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            AlertStatus.OFF -> IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.NotificationsOff,
                    contentDescription = stringResource(R.string.alerts_off),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            fun act(block: () -> Unit) {
                menuOpen = false
                block()
            }
            val snoozeMinutes = state.snoozeOptions.ifEmpty { DEFAULT_SNOOZE_MINUTES }.sorted()
            when (state.status) {
                AlertStatus.ACTIVE -> {
                    AlertMenuHeader(
                        text = stringResource(R.string.alerts_on),
                        icon = Icons.Default.Notifications,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    AlertMenuSection(stringResource(R.string.loc_snooze_all_alerts))
                    snoozeMinutes.forEach { minutes ->
                        AlertMenuItem(
                            text = stringResource(R.string.loc_snooze_for_duration, formatDuration(minutes * 60)),
                            icon = Icons.Default.Snooze,
                            onClick = { act { AlertStore.snoozeAll(minutes) } }
                        )
                    }
                    HorizontalDivider()
                    AlertMenuItem(
                        text = stringResource(R.string.alerts_turn_off),
                        icon = Icons.Default.NotificationsOff,
                        onClick = { act { AlertStore.updateSettings { it.copy(enabled = false) } } }
                    )
                }
                AlertStatus.SNOOZED -> {
                    AlertMenuHeader(
                        text = stringResource(R.string.alerts_snoozed_until, formatClock(state.snoozedUntil)),
                        icon = Icons.Default.NotificationsPaused,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    AlertMenuSection(stringResource(R.string.loc_snooze_all_alerts))
                    snoozeMinutes.forEach { minutes ->
                        AlertMenuItem(
                            text = stringResource(R.string.loc_snooze_for_duration, formatDuration(minutes * 60)),
                            icon = Icons.Default.Snooze,
                            onClick = { act { AlertStore.snoozeAll(minutes) } }
                        )
                    }
                    HorizontalDivider()
                    AlertMenuItem(
                        text = stringResource(R.string.alerts_resume),
                        icon = Icons.Default.PlayArrow,
                        onClick = { act { AlertStore.snoozeAll(0) } }
                    )
                }
                AlertStatus.OFF -> {
                    AlertMenuHeader(
                        text = stringResource(R.string.alerts_off),
                        icon = Icons.Default.NotificationsOff,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AlertMenuItem(
                        text = stringResource(R.string.alerts_turn_on),
                        icon = Icons.Default.Notifications,
                        onClick = { act { AlertStore.updateSettings { it.copy(enabled = true) } } }
                    )
                }
                AlertStatus.RINGING -> {}
            }
            HorizontalDivider()
            AlertMenuItem(
                text = stringResource(R.string.alerts_settings),
                icon = Icons.Default.Settings,
                onClick = { act(onOpenAlertSettings) }
            )
        }
    }
}

/** Snooze lengths to offer when the synced settings carry none. */
private val DEFAULT_SNOOZE_MINUTES = listOf(15, 30, 60)

/** Menu entry with a leading icon, sized and tinted the way menus do in Material 3. */
@Composable
private fun AlertMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/** Leading row telling the user what the alerts are currently doing. */
@Composable
private fun AlertMenuHeader(text: String, icon: ImageVector, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

/** Label introducing a group of menu entries, aligned with their text. */
@Composable
private fun AlertMenuSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 44.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
    )
}
