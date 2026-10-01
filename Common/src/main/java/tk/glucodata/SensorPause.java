/*      This file is part of Juggluco, an Android app to receive and display         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco.  If not, see <https://www.gnu.org/licenses/>.            */
/*                                                                                   */
/*      This file is part of Juggluco                                               */

package tk.glucodata;

import android.content.Context;

import java.util.HashSet;
import java.util.Set;

import static tk.glucodata.Log.doLog;

/**
 * Remembers which sensors the user paused with the temporary disconnect.
 *
 * A pause is not a native sensor state: the sensor keeps running, keeps its session and keeps
 * its history, Juggluco simply stops talking to it. That means nothing in the native layer
 * remembers it, so the flag lives here and is restored into every freshly created
 * {@link SuperGattCallback}. Without that, the next app start would silently reconnect a sensor
 * the user had deliberately put aside.
 */
public final class SensorPause {
    private static final String PREFS = "sensor_pause";
    private static final String KEY = "paused";

    private SensorPause() {}

    private static Context context() {
        return Applic.app;
    }

    public static boolean isPaused(String serial) {
        if (serial == null || serial.isEmpty()) return false;
        try {
            return context().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getStringSet(KEY, null)
                    .contains(serial);
        } catch (Throwable th) {
            Log.stack(LOG_ID, "isPaused " + serial, th);
            return false;
        }
    }

    /** @return true when the sensor is paused afterwards. */
    public static boolean setPaused(String serial, boolean paused) {
        if (serial == null || serial.isEmpty()) return false;
        try {
            var prefs = context().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            // getStringSet() hands back the stored instance, so it is copied before being edited.
            Set<String> serials = new HashSet<>(prefs.getStringSet(KEY, null));
            boolean changed = paused ? serials.add(serial) : serials.remove(serial);
            if (changed) {
                prefs.edit().putStringSet(KEY, serials).apply();
            }
            if (doLog) {
                Log.i(LOG_ID, "setPaused " + serial + "=" + paused + " changed=" + changed);
            }
            return paused;
        } catch (Throwable th) {
            Log.stack(LOG_ID, "setPaused " + serial, th);
            return false;
        }
    }

    private static final String LOG_ID = "SensorPause";
}