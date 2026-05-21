package com.example.sistema_inventario_inteligente.ar;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.ai.edge.litert.Accelerator;
import com.google.ai.edge.litert.CompiledModel;
import com.google.ai.edge.litert.LiteRtException;
import com.google.ai.edge.litert.TensorBuffer;

import java.util.List;

public class EmbeddingHelper {
    private static final String TAG = "EmbeddingHelper";
    private static final String MODELO = "mobilenet_v3_embedder.tflite";
    //MobileNetV3 224: lado de la imagen de entrada del modelo.
    private static final int INPUT_SIZE = 224;

    private final CompiledModel model;
    //Buffers de E/S reservados una sola vez y reutilizados en cada inferencia.
    private final List<TensorBuffer> inputBuffers;
    private final List<TensorBuffer> outputBuffers;

    public EmbeddingHelper(Context ctx) throws LiteRtException {
        // CompiledModel.create lee el .tflite directamente de assets/ y lo
        // compila para el acelerador indicado. Accelerator.CPU = ejecución en CPU.
        model = CompiledModel.create(ctx.getAssets(), MODELO,
                new CompiledModel.Options(Accelerator.CPU));
        inputBuffers = model.createInputBuffers();
        outputBuffers = model.createOutputBuffers();
        Log.i(TAG, "Modelo LiteRT cargado (CompiledModel · acelerador CPU)");
    }

    /*
     Recorte cuadrado central -> escalado -> inferencia -> L2-normalización.
     SIEMPRE debe llamarse en un hilo de fondo (la inferencia tarda ~50-150 ms).
     */
    public synchronized float[] extraer(Bitmap bmp) {
        Bitmap cuadrado = recortarCuadradoCentral(bmp);
        Bitmap resized = Bitmap.createScaledBitmap(cuadrado, INPUT_SIZE, INPUT_SIZE, true);

        // El modelo espera los píxeles en orden R,G,B y normalizados a [0,1].
        float[] entrada = new float[INPUT_SIZE * INPUT_SIZE * 3];
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        int i = 0;
        for (int px : pixels) {
            entrada[i++] = ((px >> 16) & 0xFF) / 255.0f; // R
            entrada[i++] = ((px >>  8) & 0xFF) / 255.0f; // G
            entrada[i++] = ( px        & 0xFF) / 255.0f; // B
        }

        try {
            // Se reutilizan los mismos TensorBuffer: writeFloat sobrescribe la
            // entrada, run() recalcula y readFloat() devuelve la salida fresca.
            inputBuffers.get(0).writeFloat(entrada);
            model.run(inputBuffers, outputBuffers);
            float[] salida = outputBuffers.get(0).readFloat();
            return l2Normalizar(salida);
        } catch (LiteRtException e) {
            // La firma de extraer() se mantiene sin excepción comprobada para no
            // tocar a quienes la llaman; un fallo de inferencia se propaga como
            // RuntimeException (los llamadores ya capturan Throwable/Exception).
            throw new RuntimeException("Fallo en la inferencia LiteRT", e);
        }
    }

    /*
      Recorta el mayor cuadrado centrado. Evita que un fotograma apaisado y una
      foto vertical se deformen de forma distinta al escalar, y reduce el fondo.
     */
    private Bitmap recortarCuadradoCentral(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w == h) return src;
        int lado = Math.min(w, h);
        int x = (w - lado) / 2;
        int y = (h - lado) / 2;
        return Bitmap.createBitmap(src, x, y, lado, lado);
    }

    private float[] l2Normalizar(float[] v) {
        double norma = 1e-9;
        for (float f : v) norma += (double) f * f;
        norma = Math.sqrt(norma);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norma);
        return out;
    }
    //Coseno entre dos vectores ya L2-normalizados = producto punto. Rango [-1,1].
    public static double similitudCoseno(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0;
        for (int i = 0; i < len; i++) dot += (double) a[i] * b[i];
        return dot;
    }

    //Libera el modelo y los buffers nativos. Llamar en onDestroy/onDestroyView.
    public void close() {
        for (TensorBuffer b : inputBuffers) b.close();
        for (TensorBuffer b : outputBuffers) b.close();
        model.close();
    }
}
