package tk.glucodata.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertStatus
import tk.glucodata.alerts.rememberAlertIndicatorState

@Composable
fun WearAlertIcon(
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberAlertIndicatorState()

    if (state.status == AlertStatus.RINGING) {
        val swing by rememberInfiniteTransition(label = "bell").animateFloat(
            initialValue = -18f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse),
            label = "bellSwing"
        )
        IconButton(
            onClick = { AlertPlayer.dismiss() },
            modifier = modifier,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Dismiss ${state.ringingName ?: "alert"}",
                modifier = Modifier.graphicsLayer { rotationZ = swing }
            )
        }
        return
    }

    val (icon, contentDescription, contentColor) = when (state.status) {
        AlertStatus.SNOOZED -> Triple(
            Icons.Default.NotificationsPaused,
            "Alerts snoozed",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        AlertStatus.OFF -> Triple(
            Icons.Default.NotificationsOff,
            "Alerts off",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple(
            Icons.Outlined.Notifications,
            "Alerts on",
            MaterialTheme.colorScheme.onSurface
        )
    }
    IconButton(
        onClick = onOpenAlerts,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}
