package tk.glucodata.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import org.json.JSONObject
import tk.glucodata.Applic

/**
 * Handles the Dismiss and Snooze buttons of the alert notification, the Taken, Snooze and
 * Skip buttons of medication reminders, and the alarm that wakes the device for a reminder.
 */
class AlertActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISMISS = "tk.glucodata.alerts.DISMISS"
        const val ACTION_SNOOZE = "tk.glucodata.alerts.SNOOZE"
        const val ACTION_REMINDER_ALARM = "tk.glucodata.alerts.REMINDER_ALARM"
        const val ACTION_REMINDER_TAKEN = "tk.glucodata.alerts.REMINDER_TAKEN"
        const val ACTION_REMINDER_SKIP = "tk.glucodata.alerts.REMINDER_SKIP"
        const val ACTION_REMINDER_SNOOZE = "tk.glucodata.alerts.REMINDER_SNOOZE"
        const val ACTION_REMINDER_SILENCE = "tk.glucodata.alerts.REMINDER_SILENCE"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_RULE_ID = "ruleId"
        const val EXTRA_RULE = "rule"
        const val EXTRA_DUE = "due"
        const val EXTRA_TEST = "test"

        /** Keeps the CPU up while the reminder thread does its work after onReceive returns. */
        private const val WAKE_MS = 15_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        val due = intent.getLongExtra(EXTRA_DUE, 0L)
        val isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        when (intent.action) {
            ACTION_DISMISS -> AlertPlayer.dismiss()
            ACTION_SNOOZE -> AlertPlayer.snooze(intent.getIntExtra(EXTRA_MINUTES, 15))
            ACTION_REMINDER_ALARM -> {
                holdWake(context)
                (context.applicationContext as? Applic)?.initproc()
                Reminders.refresh()
            }
            ACTION_REMINDER_TAKEN -> {
                holdWake(context)
                val fallback = intent.getStringExtra(EXTRA_RULE)?.let { runCatching { AlertRule.fromJson(JSONObject(it)) }.getOrNull() }
                Reminders.taken(ruleId, due, isTest, fallback)
            }
            ACTION_REMINDER_SKIP -> Reminders.skip(ruleId, due, isTest)
            ACTION_REMINDER_SNOOZE -> Reminders.snooze(ruleId, intent.getIntExtra(EXTRA_MINUTES, 15), isTest)
            ACTION_REMINDER_SILENCE -> Reminders.silence(ruleId)
        }
    }

    private fun holdWake(context: Context) {
        runCatching {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "juggluco:reminder").apply {
                setReferenceCounted(false)
                acquire(WAKE_MS)
            }
        }
    }
}
