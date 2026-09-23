package tk.glucodata.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import tk.glucodata.alerts.AlertKind
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertStore
import tk.glucodata.ui.theme.WearJugglucoTheme

/** Full-screen alert on the watch: value first, then Dismiss and the snooze choices. */
class WearAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WearJugglucoTheme {
                val alert by AlertPlayer.active.collectAsState()
                val settings by AlertStore.settings.collectAsState()
                LaunchedEffect(alert) {
                    if (alert == null) finish()
                }
                alert?.let {
                    WearAlertScreen(
                        alert = it,
                        snoozeOptions = settings.snoozeOptions,
                        onSnooze = { minutes -> AlertPlayer.snooze(minutes); finish() },
                        onDismiss = { AlertPlayer.dismiss(); finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WearAlertScreen(
    alert: AlertPlayer.ActiveAlert,
    snoozeOptions: List<Int>,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.errorContainer)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = alert.rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
            item {
                val reading = alert.reading
                if (alert.rule.kind == AlertKind.SIGNAL_LOSS || reading == null) {
                    Text(
                        text = "${alert.lostMinutes} min",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onErrorContainer
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reading.displayValue,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onErrorContainer
                        )
                        Text(
                            text = " " + AlertPlayer.arrow(reading.rate),
                            fontSize = 30.sp,
                            color = scheme.onErrorContainer
                        )
                    }
                }
            }
            item {
                Text(
                    text = AlertPlayer.detail(alert),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError
                    ),
                    icon = { Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                    label = { Text("Dismiss") }
                )
            }
            snoozeOptions.take(3).forEach { minutes ->
                item {
                    FilledTonalButton(
                        onClick = { onSnooze(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                        label = { Text("Snooze ${if (minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"}") }
                    )
                }
            }
        }
    }
}
