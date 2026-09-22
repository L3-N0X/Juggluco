/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Legacy GlucoseValue drawing code was replaced by the Material 3              */
/*      ComplicationRenderer. This class remains as a thin compatibility shim        */
/*      for the color-config preview UI and update triggers.                         */


package tk.glucodata.glucosecomplication;

import static tk.glucodata.glucosecomplication.ColorConfig.defcol;

import android.graphics.Bitmap;
import tk.glucodata.Natives;

public class GlucoseValue {
final private static String LOG_ID="GlucoseValue";
    static   float fontFraction=1.0f;

    final  float mapwidth;
    final float mapheight;

GlucoseValue(int w,int h) {
 	mapwidth=w;
 	mapheight=h;
    }

void clear() {
    }

Bitmap getnovalue() {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.VALUE_ONLY,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getLatestGlucose(),
        false
    );
}

Bitmap getNumberBitmap(String value,long time,int index,long now,boolean showtime) {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.VALUE_ONLY,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getLatestGlucose(),
        showtime
    );
}

Bitmap getArrowBitmap(Float rate) {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.ARROW_ONLY,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getLatestGlucose(),
        false
    );
}

Bitmap getArrowBitmap() {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.ARROW_ONLY,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getLatestGlucose(),
        false
    );
}
static int getTextColor( ) {
   int col=Natives.getComplicationTextColor( );
   return col==0?defcol[1]:col;
   }
static int getArrowColor( ) {
   int col=Natives.getComplicationArrowColor( );
   return col==0?defcol[0]:col;
   }

static int getBackgroundColor( ) {
   int col=Natives.getComplicationBackgroundColor( );
   return col==0?defcol[2]:col;
   }
Bitmap getArrowValueBitmap(String value,long time,int index,float rate,boolean showtime) {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.ARROW_BESIDE,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getLatestGlucose(),
        showtime
    );
}
Bitmap getArrowTimeBitmap(long time,float rate) {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.ARROW_ONLY,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getLatestGlucose(),
        Natives.gettimeOnComplication()
    );
}


Bitmap previewbitmap(boolean showtime) {
    return ComplicationRenderer.INSTANCE.renderComplication(
        ComplicationLayout.ARROW_BESIDE,
        ComplicationColorStyle.SIGNAL,
        ComplicationRenderer.INSTANCE.getPreviewGlucose(),
        showtime
    );
}


static public void updateall() {
    SignalArrowTopDataSourceService.Companion.update();
    SignalArrowBottomDataSourceService.Companion.update();
    ArrowValueDataSourceService.Companion.update();
    NumberDataSourceService.Companion.update();
    ArrowDataSourceService.Companion.update();
    MonoArrowTopDataSourceService.Companion.update();
    MonoArrowBottomDataSourceService.Companion.update();
    MonoArrowBesideDataSourceService.Companion.update();
    IconValueDataSourceService.Companion.update();
    IconArrowDataSourceService.Companion.update();
    ShortArrowValueDataSourceService.Companion.update();
    TimeStampComplicationService.Companion.update();
}

}
