package tk.glucodata;

import android.graphics.Bitmap;

public class QRmake {
    public static void show(Object act, String code) { }

    /** This build has no QR encoder, so a connection code can only be shown as text. */
    public static Bitmap qrbitmap(String code, int size) { return null; }
   }
