/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */

package tk.glucodata.glucosecomplication

import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import tk.glucodata.Applic

/**
 * Monochrome complication: Centered trend arrow only.
 */
class IconArrowDataSourceService : BaseGlucoseComplicationService(
    ComplicationLayout.ARROW_ONLY,
    ComplicationColorStyle.MONOCHROME
) {
    companion object {
        private val updateRequester by lazy {
            ComplicationDataSourceUpdateRequester.create(
                Applic.app,
                ComponentName(Applic.app, IconArrowDataSourceService::class.java)
            )
        }

        fun update() {
            try {
                updateRequester.requestUpdateAll()
            } catch (_: Throwable) {}
        }
    }
}
