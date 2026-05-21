package com.example.sistema_inventario_inteligente.ar;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class YuvToBitmap {

    public static Bitmap convertirDesdeBytes(byte[] yBytes, byte[] uBytes, byte[] vBytes,
                                             int width, int height, int rotacionGrados) {
        byte[] nv21 = new byte[yBytes.length + uBytes.length + vBytes.length];
        System.arraycopy(yBytes, 0, nv21, 0, yBytes.length);
        System.arraycopy(vBytes, 0, nv21, yBytes.length, vBytes.length);
        System.arraycopy(uBytes, 0, nv21, yBytes.length + vBytes.length, uBytes.length);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 90, out);
        byte[] jpegBytes = out.toByteArray();

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        if (bitmap == null) return null;

        if (rotacionGrados != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotacionGrados);
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }

    public static byte[] copiarBuffer(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }
}
