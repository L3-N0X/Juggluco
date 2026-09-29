/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Base Complication Data Source Service for Wear OS                            */

package tk.glucodata.glucosecomplication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import tk.glucodata.Log
import tk.glucodata.Notify

abstract class BaseGlucoseComplicationService(
    private val layout: ComplicationLayout,
    private val colorStyle: ComplicationColorStyle
) : SuspendingComplicationDataSourceService() {

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        Log.d(TAG, "onComplicationActivated: $complicationInstanceId, type: $type")
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated: $complicationInstanceId")
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return ComplicationRenderer.buildComplicationData(
            type = type,
            layout = layout,
            style = colorStyle,
            isPreview = true,
            pendingIntent = null
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val complicationPendingIntent = Notify.mkpendingall(this, 1002)
        return ComplicationRenderer.buildComplicationData(
            type = request.complicationType,
            layout = layout,
            style = colorStyle,
            isPreview = false,
            pendingIntent = complicationPendingIntent
        )
    }

    companion object {
        private const val TAG = "BaseGlucoseComplicationService"
    }
}
