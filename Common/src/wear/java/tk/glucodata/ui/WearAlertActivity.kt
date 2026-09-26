package tk.glucodata.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.R
import tk.glucodata.alerts.AlertKind
import tk.glucodata.alerts.AlertOutput
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertStore
import tk.glucodata.alerts.formatMinutes
import tk.glucodata.ui.theme.LocalClinicalColors
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
    val context = LocalContext.current
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val scheme = MaterialTheme.colorScheme
    val clinical = LocalClinicalColors.current
    val rule = alert.rule
    val critical = rule.output == AlertOutput.ALARM
    val glucoseColor = when (rule.kind) {
        AlertKind.LOW, AlertKind.FALLING -> if (critical) clinical.veryLow else clinical.low
        AlertKind.HIGH, AlertKind.RISING -> if (critical) clinical.veryHigh else clinical.high
        AlertKind.SIGNAL_LOSS -> scheme.onSurface
    }
    val visibleSnoozeOptions = snoozeOptions.take(3)

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.surfaceContainerLow),
            state = listState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(start = 10.dp, top = 20.dp, end = 10.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val reading = alert.reading
                    if (rule.kind == AlertKind.SIGNAL_LOSS || reading == null) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.loc_minutes_value,
                                alert.lostMinutes,
                                alert.lostMinutes
                            ),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reading.displayValue,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = glucoseColor,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = " " + AlertPlayer.arrow(reading.rate),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = glucoseColor
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = AlertPlayer.detail(alert),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    ),
                    icon = {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = { Text(stringResource(R.string.dismiss)) }
                )
            }
            if (visibleSnoozeOptions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.loc_snooze_choices),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visibleSnoozeOptions.forEach { minutes ->
                            val label = formatMinutes(context, minutes)
                            val contentLabel = stringResource(R.string.loc_snooze_duration, label)
                            FilledTonalButton(
                                onClick = { onSnooze(minutes) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = contentLabel },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = scheme.surfaceContainerHigh,
                                    contentColor = scheme.onSurface
                                ),
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
