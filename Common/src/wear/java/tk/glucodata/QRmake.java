package tk.glucodata;

import android.graphics.Bitmap;

public class QRmake {
    public static void show(Object act, String code) { }

    /** The watch has no QR encoder, and the native side exports no connection codes. */
    public static Bitmap qrbitmap(String code, int size) { return null; }
   }
