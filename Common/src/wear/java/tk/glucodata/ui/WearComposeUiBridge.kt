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

        // Reuse the existing repository when coming back from the legacy view. Building a second one
        // started a second heartbeat loop, and the first one is bound to the activity lifecycle so
        // it kept running: every round trip between the two views permanently doubled the polling
        // and the garbage it produced. A repository whose activity was destroyed has no heartbeat
        // left at all, so that one is replaced.
        val reusable = repository?.takeIf { it.isAlive }
        val repo = reusable ?: GlucoseRepository(activity.lifecycleScope).also {
            repository = it
            it.followVisibility(activity.lifecycle)
        }
        if (reusable != null) {
            activity.lifecycleScope.launch(Dispatchers.IO) { repo.refreshAll() }
        }
        tk.glucodata.alerts.AlertStore.ensureLoaded(activity)
        tk.glucodata.alerts.AlertSync.requestConfig()
        tk.glucodata.ui.sync.DisplaySync.request()

        activity.setContent {
            WearApp(
                repository = repo,
                onTriggerNfcScan = {
                    try {
                        activity.setnfc()
                        Applic.argToaster(
                            activity,
                            activity.getString(R.string.nfc_ready_instruction),
                            Toast.LENGTH_SHORT
                        )
                    } catch (e: Throwable) {
                        Applic.argToaster(
                            activity,
                            activity.getString(
                                R.string.nfc_unavailable_error,
                                e.message ?: activity.getString(R.string.failed)
                            ),
                            Toast.LENGTH_SHORT
                        )
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
