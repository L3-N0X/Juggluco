package tk.glucodata.alerts

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.screens.ScreenLayout
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
    val context = LocalContext.current
    val clinical = LocalClinicalColors.current
    val scheme = MaterialTheme.colorScheme
    val rule = alert.rule
    val critical = rule.output == AlertOutput.ALARM
    val glucoseColor = when (rule.kind) {
        AlertKind.LOW, AlertKind.FALLING -> if (critical) clinical.veryLow else clinical.low
        AlertKind.HIGH, AlertKind.RISING -> if (critical) clinical.veryHigh else clinical.high
        AlertKind.SIGNAL_LOSS -> scheme.onSurface
    }

    Surface(color = scheme.background, contentColor = scheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = ScreenLayout.Gutter, vertical = ScreenLayout.TopPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = ScreenLayout.SectionSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(ScreenLayout.SectionSpacing))
                val reading = alert.reading
                if (rule.kind == AlertKind.SIGNAL_LOSS || reading == null) {
                    Text(
                        text = pluralStringResource(R.plurals.loc_minutes_value, alert.lostMinutes, alert.lostMinutes),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reading.displayValue,
                            fontSize = 88.sp,
                            fontWeight = FontWeight.Bold,
                            color = glucoseColor,
                            letterSpacing = (-1).sp
                        )
                        val arrow = AlertPlayer.arrow(reading.rate)
                        if (arrow.isNotEmpty()) {
                            Spacer(Modifier.size(12.dp))
                            Surface(
                                shape = CircleShape,
                                color = glucoseColor.copy(alpha = 0.12f),
                                contentColor = glucoseColor,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = arrow, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ScreenLayout.SectionSpacing))
                Text(
                    text = AlertPlayer.detail(alert),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ScreenLayout.SectionSpacing)
            ) {
                Text(
                    text = stringResource(R.string.loc_snooze_choices),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ScreenLayout.SectionSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    snoozeOptions.take(3).forEach { minutes ->
                        val label = formatMinutes(context, minutes)
                        val contentLabel = stringResource(R.string.loc_snooze_duration, label)
                        FilledTonalButton(
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .semantics { contentDescription = contentLabel },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface
                            )
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun formatMinutes(context: android.content.Context, minutes: Int): String = when {
    minutes <= 0 -> context.getString(R.string.loc_common_off)
    minutes % 60 == 0 -> context.getString(R.string.loc_duration_hours, minutes / 60)
    else -> context.getString(R.string.loc_minutes_short, minutes)
}
