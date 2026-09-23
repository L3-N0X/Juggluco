package tk.glucodata.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertStatus
import tk.glucodata.alerts.AlertStore
import tk.glucodata.alerts.rememberAlertIndicatorState
import java.text.DateFormat
import java.util.Date

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
                        contentDescription = "Dismiss ${state.ringingName ?: "alert"}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.graphicsLayer { rotationZ = swing }
                    )
                }
            }
            AlertStatus.ACTIVE -> IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Alerts on")
            }
            AlertStatus.SNOOZED -> IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.NotificationsPaused,
                    contentDescription = "Alerts snoozed",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            AlertStatus.OFF -> IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.NotificationsOff,
                    contentDescription = "Alerts off",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            fun act(block: () -> Unit) {
                menuOpen = false
                block()
            }
            when (state.status) {
                AlertStatus.ACTIVE -> {
                    listOf(30 to "Snooze 30 min", 60 to "Snooze 1 h", 120 to "Snooze 2 h", 480 to "Snooze 8 h").forEach { (minutes, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { act { AlertStore.snoozeAll(minutes) } })
                    }
                    DropdownMenuItem(
                        text = { Text("Turn alerts off") },
                        onClick = { act { AlertStore.updateSettings { it.copy(enabled = false) } } }
                    )
                }
                AlertStatus.SNOOZED -> {
                    DropdownMenuItem(
                        text = { Text("Snoozed until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.snoozedUntil))}") },
                        onClick = {},
                        enabled = false
                    )
                    DropdownMenuItem(text = { Text("Resume alerts") }, onClick = { act { AlertStore.snoozeAll(0) } })
                }
                AlertStatus.OFF -> DropdownMenuItem(
                    text = { Text("Turn alerts on") },
                    onClick = { act { AlertStore.updateSettings { it.copy(enabled = true) } } }
                )
                AlertStatus.RINGING -> {}
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Alert settings") }, onClick = { act(onOpenAlertSettings) })
        }
    }
}
