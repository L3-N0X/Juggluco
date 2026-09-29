package tk.glucodata.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Dismiss and Snooze buttons of the alert notification. */
class AlertActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISMISS = "tk.glucodata.alerts.DISMISS"
        const val ACTION_SNOOZE = "tk.glucodata.alerts.SNOOZE"
        const val EXTRA_MINUTES = "minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> AlertPlayer.dismiss()
            ACTION_SNOOZE -> AlertPlayer.snooze(intent.getIntExtra(EXTRA_MINUTES, 15))
        }
    }
}
