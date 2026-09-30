package tk.glucodata.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import tk.glucodata.Notify

/**
 * Whether the reading taken at [timestamp] (epoch milliseconds) is no longer current. Turns true by
 * itself once the reading ages past [Notify.glucosetimeout], without waiting for a new one.
 */
@Composable
fun rememberIsStale(timestamp: Long?): Boolean {
    val stale by produceState(timestamp == null || isStale(timestamp), timestamp) {
        if (timestamp == null) return@produceState
        while (!value) {
            delay((timestamp + Notify.glucosetimeout - System.currentTimeMillis()).coerceAtLeast(1_000L))
            value = isStale(timestamp)
        }
    }
    return stale
}

private fun isStale(timestamp: Long) = System.currentTimeMillis() - timestamp > Notify.glucosetimeout
