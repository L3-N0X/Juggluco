package tk.glucodata.ui

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import tk.glucodata.Applic
import tk.glucodata.MainActivity
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

object ComposeUiBridge {
    @JvmField
    var isComposeUiActive: Boolean = true

    @JvmField
    var repository: GlucoseRepository? = null

    @JvmStatic
    fun setupComposeUi(activity: MainActivity): GlucoseRepository {
        isComposeUiActive = true

        // Allow natural portrait orientation with sensor auto-rotation
        try {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } catch (_: Throwable) {}

        val repo = GlucoseRepository(activity.lifecycleScope)
        repository = repo
        tk.glucodata.alerts.AlertStore.ensureLoaded(activity)
        tk.glucodata.alerts.AlertSync.pushConfig()

        activity.setContent {
            JugglucoApp(
                repository = repo,
                onTriggerNfcScan = {
                    try {
                        activity.setnfc()
                        Applic.argToaster(activity, activity.getString(R.string.nfc_ready_instruction), Toast.LENGTH_SHORT)
                    } catch (e: Throwable) {
                        Applic.argToaster(activity, activity.getString(R.string.nfc_unavailable_error, e.message), Toast.LENGTH_SHORT)
                    }
                },
                onOpenLegacyView = {
                    switchToLegacyView(activity)
                },
                onExportData = {
                    // Handled within Compose UI by dedicated ExportScreen
                }
            )
        }

        return repo
    }

    @JvmStatic
    fun switchToLegacyView(activity: MainActivity) {
        isComposeUiActive = false
        val c = activity.curve
        if (c != null) {
            activity.setContentView(c)
            activity.applyScreenOrientation(activity.resources.configuration)
            c.requestRender()
            Applic.argToaster(activity, activity.getString(R.string.switched_to_legacy_view), Toast.LENGTH_SHORT)
            MainActivity.setonback {
                switchToComposeUi(activity)
            }
        }
    }

    @JvmStatic
    fun switchToComposeUi(activity: MainActivity) {
        setupComposeUi(activity)
    }
}
