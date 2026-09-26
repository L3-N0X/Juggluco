package tk.glucodata.ui

import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.Applic
import tk.glucodata.MainActivity
import tk.glucodata.MessageSender
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

object WearComposeUiBridge {
    @JvmField
    var isWearComposeUiActive: Boolean = true

    @JvmField
    var repository: GlucoseRepository? = null

    @JvmStatic
    fun setupWearComposeUi(activity: MainActivity): GlucoseRepository {
        isWearComposeUiActive = true

        val repo = GlucoseRepository(activity.lifecycleScope)
        repository = repo
        tk.glucodata.alerts.AlertStore.ensureLoaded(activity)
        tk.glucodata.alerts.AlertSync.requestConfig()

        activity.setContent {
            WearApp(
                repository = repo,
                onTriggerNfcScan = {
                    try {
                        activity.setnfc()
                        Applic.argToaster(activity, "NFC ready: Hold watch to sensor", Toast.LENGTH_SHORT)
                    } catch (e: Throwable) {
                        Applic.argToaster(activity, "NFC error: ${e.message}", Toast.LENGTH_SHORT)
                    }
                },
                onSyncPhone = {
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            Applic.switchSync()
                            MessageSender.reinit()
                            withContext(Dispatchers.Main.immediate) {
                                repo.refreshMirrorConnections()
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.wear_phone_sync_started),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Throwable) {
                            withContext(Dispatchers.Main.immediate) {
                                Toast.makeText(
                                    activity,
                                    activity.getString(
                                        R.string.wear_phone_sync_error,
                                        e.message ?: activity.getString(R.string.failed)
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                onOpenLegacyView = {
                    switchToLegacyView(activity)
                }
            )
        }

        return repo
    }

    @JvmStatic
    fun switchToLegacyView(activity: MainActivity) {
        isWearComposeUiActive = false
        val c = activity.curve
        if (c != null) {
            activity.setContentView(c)
            c.requestRender()
            MainActivity.setonback {
                switchToWearComposeUi(activity)
            }
        }
    }

    @JvmStatic
    fun switchToWearComposeUi(activity: MainActivity) {
        setupWearComposeUi(activity)
    }
}
