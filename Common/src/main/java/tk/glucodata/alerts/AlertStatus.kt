package tk.glucodata.alerts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** What the alert indicator on the home screens shows. */
enum class AlertStatus {
    /** An alert is ringing; tapping the indicator dismisses it everywhere. */
    RINGING,
    /** Alerts are on and not snoozed. */
    ACTIVE,
    /** All alerts are snoozed until [AlertIndicatorState.snoozedUntil]. */
    SNOOZED,
    /** Alerts are switched off on this device. */
    OFF
}

data class AlertIndicatorState(
    val status: AlertStatus,
    val snoozedUntil: Long,
    val ringingName: String?
)

/** Current indicator state, re-evaluated when a snooze runs out. */
@Composable
fun rememberAlertIndicatorState(): AlertIndicatorState {
    remember { AlertStore.ensureLoaded() }
    val active by AlertPlayer.active.collectAsState()
    val settings by AlertStore.settings.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.snoozeAllUntil) {
        now = System.currentTimeMillis()
        val wait = settings.snoozeAllUntil - System.currentTimeMillis()
        if (wait > 0) {
            delay(wait + 500)
            now = System.currentTimeMillis()
        }
    }
    val ringing = active?.takeIf { it.ringing }
    val status = when {
        ringing != null -> AlertStatus.RINGING
        !settings.enabled -> AlertStatus.OFF
        settings.snoozeAllUntil > now -> AlertStatus.SNOOZED
        else -> AlertStatus.ACTIVE
    }
    return AlertIndicatorState(status, settings.snoozeAllUntil, ringing?.rule?.name)
}
