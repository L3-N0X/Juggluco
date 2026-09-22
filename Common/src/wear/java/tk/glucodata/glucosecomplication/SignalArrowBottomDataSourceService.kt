/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */

package tk.glucodata.glucosecomplication

import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import tk.glucodata.Applic

/**
 * Signal complication: Number with small trend arrow below.
 */
class SignalArrowBottomDataSourceService : BaseGlucoseComplicationService(
    ComplicationLayout.ARROW_BOTTOM,
    ComplicationColorStyle.SIGNAL
) {
    companion object {
        private val updateRequester by lazy {
            ComplicationDataSourceUpdateRequester.create(
                Applic.app,
                ComponentName(Applic.app, SignalArrowBottomDataSourceService::class.java)
            )
        }

        fun update() {
            try {
                updateRequester.requestUpdateAll()
            } catch (_: Throwable) {}
        }
    }
}
