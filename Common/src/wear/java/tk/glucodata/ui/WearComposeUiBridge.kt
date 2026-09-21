package tk.glucodata.ui

import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import tk.glucodata.Applic
import tk.glucodata.MainActivity
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
