package tk.glucodata.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertStatus
import tk.glucodata.alerts.rememberAlertIndicatorState
import tk.glucodata.ui.screens.settings.formatClock

/**
 * Home screen alert state. Ringing: a swinging bell, and a tap dismisses the alert on
 * the watch and the phone. Otherwise it shows whether alerts are on, snoozed or off
 * and opens the alerts screen.
 */
@Composable
fun WearAlertButton(onOpenAlerts: () -> Unit) {
    val state = rememberAlertIndicatorState()
    val iconModifier = Modifier.size(ButtonDefaults.IconSize)

    if (state.status == AlertStatus.RINGING) {
        val swing by rememberInfiniteTransition(label = "bell").animateFloat(
            initialValue = -18f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
            label = "bellSwing"
        )
        Button(
            onClick = { AlertPlayer.dismiss() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                secondaryContentColor = MaterialTheme.colorScheme.onError,
                iconColor = MaterialTheme.colorScheme.onError
            ),
            icon = {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = iconModifier.graphicsLayer { rotationZ = swing }
                )
            },
            label = { Text("Dismiss", maxLines = 1) },
            secondaryLabel = { Text(state.ringingName ?: "Alert", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        )
        return
    }

    val (icon, label, secondary) = when (state.status) {
        AlertStatus.SNOOZED -> Triple(Icons.Default.NotificationsPaused, "Snoozed", "Until ${formatClock(state.snoozedUntil)}")
        AlertStatus.OFF -> Triple(Icons.Default.NotificationsOff, "Alerts off", null)
        else -> Triple(Icons.Outlined.Notifications, "Alerts on", null)
    }
    FilledTonalButton(
        onClick = onOpenAlerts,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(icon, contentDescription = null, modifier = iconModifier) },
        label = { Text(label, maxLines = 1) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 1) } }
    )
}
