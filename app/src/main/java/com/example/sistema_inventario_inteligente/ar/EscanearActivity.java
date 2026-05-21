package com.example.sistema_inventario_inteligente.ar;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.sistema_inventario_inteligente.DetalleProductoActivity;
import com.example.sistema_inventario_inteligente.R;
import com.example.sistema_inventario_inteligente.databinding.ActivityEscanearBinding;
import com.example.sistema_inventario_inteligente.glide.GlideApp;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.ar.node.AnchorNode;
import io.github.sceneview.node.ModelNode;

public class EscanearActivity extends AppCompatActivity {
    public static final String EXTRA_PRODUCTO_ID = "productoId";

    private static final String TAG = "EscanearActivity";
    private static final float MODELO_ESCALA_METROS = 0.3f;
    private static final double UMBRAL_SIMILITUD = 0.62;
    private static final int FRAMES_CONFIRMACION = 3;
    private static final long INTERVALO_ANALISIS_MS = 600;
    private static final long TIMEOUT_AYUDA_MS = 15_000;

    private enum Modo { CARGANDO, ESCANEANDO }

    private ActivityEscanearBinding binding;
    private final ProductoContrato repositorio = new ProductoRepository();
    private EmbeddingHelper embeddingHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Estado AR
    private Frame lastFrame = null;
    private AnchorNode anchorActual = null;
    private Anchor anchorARCore = null;
    private boolean modeloColocado = false;
    private boolean sessionConfigurada = false;
    private String productoIdActual = null;
    private Producto productoActual = null;

    // Modo de operación
    private Modo modoActual = Modo.CARGANDO;
    private final Handler handlerTimeout = new Handler(Looper.getMainLooper());
    private final Runnable ayudaRunnable = this::mostrarAyudaEscaneo;

    // Embedding loop
    private final AtomicBoolean analizando = new AtomicBoolean(false);
    private long ultimoAnalisisMs = 0;
    private List<Producto> productosConVectores = new ArrayList<>();
    private boolean modeloIADisponible = false;
    private boolean productoConfirmado = false;
    private String candidatoId = null;
    private int votosCandidato = 0;

    // Animación retículo
    private ObjectAnimator pulsoReticuloAnimator = null;

    private boolean etiquetaYaAnimada = false;
    private boolean panelMinimizado = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Snackbar.make(binding.getRoot(),
                            "Se necesita permiso de cámara para AR.", Snackbar.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEscanearBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnCerrar.setOnClickListener(v -> finish());
        binding.btnVerFichaAR.setOnClickListener(v -> abrirFichaProducto());
        binding.btnCerrarInfoAR.setOnClickListener(v -> ocultarInfoProducto());
        binding.btnMinimizarPanel.setOnClickListener(v -> toggleMinimizarPanel());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        productoIdActual = getIntent().getStringExtra(EXTRA_PRODUCTO_ID);
        if (productoIdActual != null) {
            cargarProductoDesdeFirebase(productoIdActual);
        }

        configurarFrameListener();
        configurarTouchListener();
        iniciarPulsoReticulo();
        iniciarCargaEmbeddings();
    }

    // ── Retículo pulsante ─────────────────────────────────────────────────
    private void iniciarPulsoReticulo() {
        pulsoReticuloAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.viewReticuloRing,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.35f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.35f, 1f),
                PropertyValuesHolder.ofFloat("alpha", 0.7f, 0.2f, 0.7f)
        );
        pulsoReticuloAnimator.setDuration(1500);
        pulsoReticuloAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulsoReticuloAnimator.start();
    }

    private void detenerPulsoReticulo() {
        if (pulsoReticuloAnimator != null) {
            pulsoReticuloAnimator.cancel();
            pulsoReticuloAnimator = null;
        }
        binding.layoutReticulo.setVisibility(View.GONE);
    }

    // ── Configuración de la sesión ARCore ─────────────────────────────────
    private void configurarFrameListener() {
        binding.arSceneView.setOnSessionUpdated((session, frame) -> {
            lastFrame = frame;

            if (!sessionConfigurada) {
                sessionConfigurada = true;
                runOnUiThread(() -> {
                    try {
                        Config cfg = session.getConfig();
                        cfg.setFocusMode(Config.FocusMode.AUTO);
                        cfg.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                        cfg.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
                        cfg.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                        session.configure(cfg);
                    } catch (Exception e) {
                        Log.e(TAG, "Error configurando sesión", e);
                    }
                });
            }

            // Embedding loop — solo al escanear, sin modelo colocado ni producto confirmado
            if (modoActual == Modo.ESCANEANDO && !modeloColocado && !productoConfirmado
                    && embeddingHelper != null && !productosConVectores.isEmpty()) {
                long ahora = System.currentTimeMillis();
                if (ahora - ultimoAnalisisMs >= INTERVALO_ANALISIS_MS
                        && analizando.compareAndSet(false, true)) {
                    ultimoAnalisisMs = ahora;

                    Image camImg = null;
                    byte[] yBytes = null, uBytes = null, vBytes = null;
                    int w = 0, h = 0;
                    try {
                        camImg = frame.acquireCameraImage();
                        w = camImg.getWidth();
                        h = camImg.getHeight();
                        yBytes = YuvToBitmap.copiarBuffer(camImg.getPlanes()[0].getBuffer());
                        uBytes = YuvToBitmap.copiarBuffer(camImg.getPlanes()[1].getBuffer());
                        vBytes = YuvToBitmap.copiarBuffer(camImg.getPlanes()[2].getBuffer());
                    } catch (Exception e) {
                        analizando.set(false);
                    } finally {
                        if (camImg != null) camImg.close();
                    }

                    if (yBytes != null) {
                        final byte[] fY = yBytes, fU = uBytes, fV = vBytes;
                        final int fw = w, fh = h;
                        executor.execute(() -> procesarFrameEmbedding(fY, fU, fV, fw, fh));
                    }
                }
            }

            if (modeloColocado && anchorARCore != null && productoActual != null) {
                proyectarEtiqueta(frame, anchorARCore);
            }

            return kotlin.Unit.INSTANCE;
        });
    }

    private void procesarFrameEmbedding(byte[] yBytes, byte[] uBytes, byte[] vBytes, int w, int h) {
        try {
            android.graphics.Bitmap bmp =
                    YuvToBitmap.convertirDesdeBytes(yBytes, uBytes, vBytes, w, h, 90);
            if (bmp == null) return;
            float[] vectorVivo = embeddingHelper.extraer(bmp);

            Producto mejor = null;
            double mejorSim = -1.0;
            for (Producto p : productosConVectores) {
                if (p.getVectoresIA() == null) continue;
                double sim = similitudConProducto(vectorVivo, p);
                if (sim > mejorSim) {
                    mejorSim = sim;
                    mejor = p;
                }
            }

            final Producto fMejor = mejor;
            final double fSim = mejorSim;
            runOnUiThread(() -> evaluarCandidato(fMejor, fSim));
        } catch (Throwable e) {
            Log.w(TAG, "Error en embedding del frame", e);
        } finally {
            analizando.set(false);
        }
    }

    private void evaluarCandidato(Producto candidato, double similitud) {
        if (binding == null || modeloColocado || productoConfirmado) return;

        int pct = (int) Math.round(Math.max(0.0, similitud) * 100);
        binding.tvDebugScore.setText(
                (candidato != null ? candidato.getNombre() : "Sin coincidencia") + "  ·  " + pct + "%");

        if (candidato != null && candidato.getIdProducto() != null && similitud >= UMBRAL_SIMILITUD) {
            if (candidato.getIdProducto().equals(candidatoId)) {
                votosCandidato++;
            } else {
                candidatoId = candidato.getIdProducto();
                votosCandidato = 1;
            }
            if (votosCandidato >= FRAMES_CONFIRMACION) {
                productoConfirmado = true;
                binding.tvDebugScore.setVisibility(View.GONE);
                onProductoIdentificado(candidato);
            }
        } else {
            candidatoId = null;
            votosCandidato = 0;
        }
    }

    private double similitudConProducto(float[] vectorVivo, Producto p) {
        if (p.getVectoresIA().getVector() == null) return 0.0;
        return EmbeddingHelper.similitudCoseno(vectorVivo, listToFloat(p.getVectoresIA().getVector()));
    }

    private float[] listToFloat(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
        return arr;
    }

    private void onProductoIdentificado(Producto p) {
        handlerTimeout.removeCallbacks(ayudaRunnable);
        binding.layoutAyuda.setVisibility(View.GONE);
        productoActual = p;
        productoIdActual = p.getIdProducto();
        binding.tvEstadoAR.setText("PRODUCTO DETECTADO");
        binding.tvInstruccion.setText("Toca una superficie para colocar: " + p.getNombre());
        mostrarInfoProducto(p);
    }

    private void configurarTouchListener() {
        binding.arSceneView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    && lastFrame != null
                    && modoActual == Modo.ESCANEANDO
                    && !modeloColocado) {
                procesarToque(event.getX(), event.getY());
            }
            return true;
        });
    }

    // ── Carga inicial: productos + modelo TFLite ──────────────────────────

    private void iniciarCargaEmbeddings() {
        binding.layoutCargando.setVisibility(View.VISIBLE);
        binding.tvCargandoTexto.setText("Cargando modelo de detección...");

        executor.execute(() -> {
            try {
                embeddingHelper = new EmbeddingHelper(this);
                modeloIADisponible = true;
            } catch (Throwable e) {
                modeloIADisponible = false;
                Log.e(TAG, "No se pudo cargar el modelo TFLite", e);
            }
            runOnUiThread(this::cargarProductosConVectores);
        });
    }

    private void cargarProductosConVectores() {
        binding.tvCargandoTexto.setText("Cargando imágenes de referencia...");

        repositorio.obtenerProductosEnTiempoReal("", new ProductoContrato.LeerCallback() {
            @Override
            public void onProductosCargados(List<Producto> productos) {
                repositorio.detenerEscucha();
                productosConVectores = new ArrayList<>();
                for (Producto p : productos) {
                    if (p.getVectoresIA() != null && p.getVectoresIA().getVector() != null) {
                        productosConVectores.add(p);
                    }
                }
                activarModoEscaneando();
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Error al obtener productos: " + error);
                activarModoEscaneando();
            }
        });
    }

    // ── Transición de modo ────────────────────────────────────────────────
    private void activarModoEscaneando() {
        modoActual = Modo.ESCANEANDO;
        binding.layoutCargando.setVisibility(View.GONE);
        binding.cardEstado.setVisibility(View.VISIBLE);

        if (modeloIADisponible && !productosConVectores.isEmpty()) {
            binding.tvEstadoAR.setText("DETECTANDO PRODUCTOS...");
            binding.tvInstruccion.setText("Centra el producto dentro del círculo");
            binding.tvDebugScore.setText("Analizando...");
            binding.tvDebugScore.setVisibility(View.VISIBLE);
        } else {
            binding.tvEstadoAR.setText("MODO MANUAL");
            binding.tvInstruccion.setText(!modeloIADisponible
                    ? "Modelo IA no disponible · toca una superficie para colocar el modelo"
                    : "Aún no hay productos con IA registrados · toca una superficie");
        }
        handlerTimeout.postDelayed(ayudaRunnable, TIMEOUT_AYUDA_MS);
    }

    private void toggleMinimizarPanel() {
        panelMinimizado = !panelMinimizado;
        if (panelMinimizado) {
            binding.tvInstruccion.setVisibility(View.GONE);
            binding.layoutInfoProducto.setVisibility(View.GONE);
            binding.btnMinimizarPanel.animate().rotation(270f).setDuration(200).start();
        } else {
            binding.tvInstruccion.setVisibility(View.VISIBLE);
            binding.layoutInfoProducto.setVisibility(View.VISIBLE);
            binding.btnMinimizarPanel.animate().rotation(90f).setDuration(200).start();
        }
    }

    // ── Overlay de ayuda ──────────────────────────────────────────────────
    private void mostrarAyudaEscaneo() {
        if (modeloColocado || productoConfirmado) return;
        binding.tvAyudaTitulo.setText("No detectamos ningún producto");
        binding.tvAyudaSubtitulo.setText(
                "El encuadre no coincide con ningún producto registrado.\nSigue con estos consejos.");
        binding.layoutTips.removeAllViews();
        agregarTip("Centra el producto y llena el encuadre");
        agregarTip("Mejora la iluminación del entorno");
        agregarTip("Evita fondos saturados o muy similares al producto");
        binding.btnAyudaContinuar.setText("Seguir intentando");
        binding.btnAyudaContinuar.setOnClickListener(v -> {
            binding.layoutAyuda.setVisibility(View.GONE);
            handlerTimeout.postDelayed(ayudaRunnable, TIMEOUT_AYUDA_MS);
        });
        binding.layoutAyuda.setVisibility(View.VISIBLE);
    }

    private void agregarTip(String texto) {
        TextView tip = new TextView(this);
        tip.setText("· " + texto);
        tip.setTextColor(0xCCFFFFFF);
        tip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        tip.setLineSpacing(4f, 1f);
        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        tip.setLayoutParams(params);
        binding.layoutTips.addView(tip);
    }

    // ── hitTest + colocación de modelo ────────────────────────────────────
    private void procesarToque(float x, float y) {
        List<HitResult> hits = lastFrame.hitTest(x, y);
        for (HitResult hit : hits) {
            Trackable trackable = hit.getTrackable();
            if (trackable instanceof Plane
                    && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                colocarModeloEnHit(hit, (Plane) trackable);
                break;
            }
        }
    }

    private void colocarModeloEnHit(HitResult hit, Plane plane) {
        try {
            Anchor anchor = hit.createAnchor();
            anchorARCore = anchor;
            anchorActual = new AnchorNode(
                    binding.arSceneView.getEngine(), anchor, null, null, null, null);
            binding.arSceneView.addChildNode(anchorActual);

            if (productoActual != null
                    && productoActual.getUrlModelo3D() != null
                    && !productoActual.getUrlModelo3D().isEmpty()) {
                cargarModelo3D(productoActual.getUrlModelo3D());
            }

            detenerPulsoReticulo();
            binding.tvDebugScore.setVisibility(View.GONE);
            handlerTimeout.removeCallbacks(ayudaRunnable);

            if (productoActual != null) {
                runOnUiThread(() -> mostrarInfoProducto(productoActual));
            } else {
                runOnUiThread(() -> {
                    binding.tvEstadoAR.setText("MODELO COLOCADO");
                    binding.tvInstruccion.setText("Toca 'Cerrar' para reposicionar");
                });
            }
            modeloColocado = true;
        } catch (Exception e) {
            Log.e(TAG, "Error al crear anchor por hitTest", e);
        }
    }

    // ── Carga del modelo 3D y datos ───────────────────────────────────────
    private void cargarProductoDesdeFirebase(String productoId) {
        repositorio.obtenerProductoId(productoId, new ProductoContrato.LeerIdCallback() {
            @Override
            public void onProductoCargado(Producto productoObtenido) {
                productoActual = productoObtenido;
                runOnUiThread(() -> binding.tvInstruccion.setText(
                        "Toca una superficie para colocar: " + productoObtenido.getNombre()));
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "No se pudo precargar producto: " + error);
            }
        });
    }

    private void cargarModelo3D(String url) {
        if (anchorActual == null) return;
        binding.arSceneView.getModelLoader().loadModelInstanceAsync(
                url,
                name -> null,
                modelInstance -> {
                    if (modelInstance != null) {
                        ModelNode modelNode = new ModelNode(
                                modelInstance, true,
                                MODELO_ESCALA_METROS,
                                new Float3(0f, 0f, 0f));
                        runOnUiThread(() -> anchorActual.addChildNode(modelNode));
                    }
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    // ── UI del panel inferior ─────────────────────────────────────────────
    private void mostrarInfoProducto(Producto p) {
        etiquetaYaAnimada = false;
        mostrarEtiqueta(p);
        binding.tvEstadoAR.setText("PRODUCTO DETECTADO");
        binding.tvInstruccion.setText(p.getNombre());
        binding.tvProductoNombreAR.setText(p.getNombre());
        binding.tvProductoSkuAR.setText("—");
        binding.tvProductoPrecioAR.setText(String.format(Locale.US, "$%.2f", p.getPrecio()));

        if (p.getCategoria() != null && !p.getCategoria().isEmpty()) {
            binding.tvProductoCategoriaAR.setText(p.getCategoria());
            binding.tvProductoCategoriaAR.setVisibility(View.VISIBLE);
        } else {
            binding.tvProductoCategoriaAR.setVisibility(View.GONE);
        }

        if (p.getUrlImagenProducto() != null && !p.getUrlImagenProducto().isEmpty()) {
            GlideApp.with(this)
                    .load(FirebaseStorage.getInstance().getReferenceFromUrl(p.getUrlImagenProducto()))
                    .centerCrop()
                    .into(binding.ivProductoThumbAR);
            binding.ivProductoThumbAR.setVisibility(View.VISIBLE);
        } else {
            binding.ivProductoThumbAR.setVisibility(View.GONE);
        }

        panelMinimizado = false;
        binding.tvInstruccion.setVisibility(View.VISIBLE);
        binding.btnMinimizarPanel.setVisibility(View.VISIBLE);
        binding.btnMinimizarPanel.setRotation(90f);

        binding.layoutInfoProducto.setVisibility(View.VISIBLE);
    }

    private void mostrarEtiqueta(Producto p) {
        binding.tvEtiquetaNombre.setText(p.getNombre());
        binding.tvEtiquetaPrecio.setText(String.format(Locale.US, "$%.2f", p.getPrecio()));
    }

    private void proyectarEtiqueta(Frame frame, Anchor anchor) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) {
            runOnUiThread(() -> binding.layoutEtiquetaFlotante.setVisibility(View.GONE));
            return;
        }

        float[] t = anchor.getPose().getTranslation();
        float[] punto = {t[0], t[1] + 0.30f, t[2], 1f};

        float[] viewMatrix = new float[16];
        float[] projMatrix = new float[16];
        frame.getCamera().getViewMatrix(viewMatrix, 0);
        frame.getCamera().getProjectionMatrix(projMatrix, 0, 0.1f, 100f);

        float[] mv = new float[4];
        Matrix.multiplyMV(mv, 0, viewMatrix, 0, punto, 0);

        if (mv[2] >= 0) {
            runOnUiThread(() -> binding.layoutEtiquetaFlotante.setVisibility(View.GONE));
            return;
        }

        float[] clip = new float[4];
        Matrix.multiplyMV(clip, 0, projMatrix, 0, mv, 0);

        float ndcX = clip[0] / clip[3];
        float ndcY = clip[1] / clip[3];

        if (Math.abs(ndcX) > 1.2f || Math.abs(ndcY) > 1.2f) {
            runOnUiThread(() -> binding.layoutEtiquetaFlotante.setVisibility(View.GONE));
            return;
        }

        int screenW = binding.arSceneView.getWidth();
        int screenH = binding.arSceneView.getHeight();
        if (screenW == 0 || screenH == 0) return;

        float screenX = (ndcX + 1f) * 0.5f * screenW;
        float screenY = (1f - ndcY) * 0.5f * screenH;

        runOnUiThread(() -> {
            boolean estabaVisible = binding.layoutEtiquetaFlotante.getVisibility() == View.VISIBLE;
            binding.layoutEtiquetaFlotante.setVisibility(View.VISIBLE);
            binding.layoutEtiquetaFlotante.setTranslationX(
                    screenX - binding.layoutEtiquetaFlotante.getWidth() / 2f);
            binding.layoutEtiquetaFlotante.setTranslationY(
                    screenY - binding.layoutEtiquetaFlotante.getHeight() / 2f);

            if (!estabaVisible && !etiquetaYaAnimada) {
                etiquetaYaAnimada = true;
            }
        });
    }

    private void ocultarInfoProducto() {
        binding.layoutInfoProducto.setVisibility(View.GONE);

        if (anchorActual != null) {
            binding.arSceneView.removeChildNode(anchorActual);
            anchorActual.destroy();
            anchorActual = null;
        }
        anchorARCore = null;
        modeloColocado = false;
        etiquetaYaAnimada = false;
        panelMinimizado = false;
        binding.layoutEtiquetaFlotante.setVisibility(View.GONE);
        binding.btnMinimizarPanel.setVisibility(View.GONE);
        binding.tvInstruccion.setVisibility(View.VISIBLE);

        binding.layoutReticulo.setVisibility(View.VISIBLE);
        iniciarPulsoReticulo();

        productoConfirmado = false;
        candidatoId = null;
        votosCandidato = 0;

        if (modeloIADisponible && !productosConVectores.isEmpty()) {
            binding.tvEstadoAR.setText("DETECTANDO PRODUCTOS...");
            binding.tvInstruccion.setText("Centra el producto dentro del círculo");
            binding.tvDebugScore.setText("Analizando...");
            binding.tvDebugScore.setVisibility(View.VISIBLE);
        } else {
            binding.tvEstadoAR.setText("MODO MANUAL");
            binding.tvInstruccion.setText("Toca una superficie para colocar el modelo");
        }
        handlerTimeout.postDelayed(ayudaRunnable, TIMEOUT_AYUDA_MS);
    }

    private void abrirFichaProducto() {
        if (productoIdActual == null) return;
        Intent intent = new Intent(this, DetalleProductoActivity.class);
        intent.putExtra("idProducto", productoIdActual);
        startActivity(intent);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handlerTimeout.removeCallbacks(ayudaRunnable);
        if (pulsoReticuloAnimator != null) {
            pulsoReticuloAnimator.cancel();
            pulsoReticuloAnimator = null;
        }
        repositorio.detenerEscucha();
        if (embeddingHelper != null) {
            embeddingHelper.close();
            embeddingHelper = null;
        }
        executor.shutdownNow();
        anchorActual = null;
        anchorARCore = null;
    }
}
