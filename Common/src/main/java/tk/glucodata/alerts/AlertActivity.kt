package tk.glucodata.alerts

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import tk.glucodata.ui.theme.JugglucoTheme
import tk.glucodata.ui.theme.LocalClinicalColors

/** Full-screen alert shown over the lock screen for alerts with "Full-screen alert" on. */
class AlertActivity : ComponentActivity() {
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
            JugglucoTheme(darkTheme = isSystemInDarkTheme()) {
                val alert by AlertPlayer.active.collectAsState()
                val settings by AlertStore.settings.collectAsState()
                LaunchedEffect(alert) {
                    if (alert == null) finish()
                }
                alert?.let {
                    AlertScreen(
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
private fun AlertScreen(
    alert: AlertPlayer.ActiveAlert,
    snoozeOptions: List<Int>,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val clinical = LocalClinicalColors.current
    val rule = alert.rule
    val critical = rule.output == AlertOutput.ALARM
    val (container, onContainer) = when (rule.kind) {
        AlertKind.LOW, AlertKind.FALLING ->
            if (critical) clinical.veryLowContainer to clinical.onVeryLowContainer
            else clinical.lowContainer to clinical.onLowContainer
        AlertKind.HIGH, AlertKind.RISING ->
            if (critical) clinical.veryHighContainer to clinical.onVeryHighContainer
            else clinical.highContainer to clinical.onHighContainer
        AlertKind.SIGNAL_LOSS ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(color = container, contentColor = onContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                val reading = alert.reading
                if (rule.kind == AlertKind.SIGNAL_LOSS || reading == null) {
                    Text(
                        text = "${alert.lostMinutes} min",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = reading.displayValue, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = " " + AlertPlayer.arrow(reading.rate),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = AlertPlayer.detail(alert),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    snoozeOptions.take(3).forEach { minutes ->
                        FilledTonalButton(
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("Snooze ${formatMinutes(minutes)}", maxLines = 1)
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onContainer,
                        contentColor = container
                    )
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

internal fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "Off"
    minutes % 60 == 0 -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}
