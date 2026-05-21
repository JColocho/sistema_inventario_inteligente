package com.example.sistema_inventario_inteligente.ar;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
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
    private static final double UMBRAL_SIMILITUD = 0.50f;
    private static final int FRAMES_CONFIRMACION = 3;
    private static final long INTERVALO_ANALISIS_MS = 600;

    // Gestos de movimiento
    private static final float DRAG_THRESHOLD_PX = 18f;
    private static final long MOVE_INTERVALO_MS = 80;

    private enum Modo { CARGANDO, ESCANEANDO }

    private ActivityEscanearBinding binding;
    private final ProductoContrato repositorio = new ProductoRepository();
    private EmbeddingHelper embeddingHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Estado AR
    private Frame lastFrame = null;
    private AnchorNode anchorActual = null;
    private Anchor anchorARCore = null;
    private ModelNode modelNodeActual = null;
    private boolean modeloColocado = false;
    private boolean sessionConfigurada = false;
    private String productoIdActual = null;
    private Producto productoActual = null;

    // Modo de operación
    private Modo modoActual = Modo.CARGANDO;

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

    // Gesto arrastrar para mover el modelo
    private float dragStartX, dragStartY;
    private boolean estaArrastrando = false;
    private long ultimoMoveMs = 0;

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

    //Retículo pulsante
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

    //Configuración de la sesión ARCore
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
        if (p.getVectoresIA() == null) return 0.0;
        return EmbeddingHelper.similitudCoseno(vectorVivo, listToFloat(p.getVectoresIA()));
    }

    private float[] listToFloat(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
        return arr;
    }

    private void onProductoIdentificado(Producto p) {
        productoActual = p;
        productoIdActual = p.getIdProducto();
        binding.tvEstadoAR.setText("PRODUCTO DETECTADO");
        binding.tvInstruccion.setText(p.getNombre());
        // Mostrar hint no invasivo: buscar superficie
        binding.layoutHintSuperficie.setVisibility(View.VISIBLE);
    }

    //Touch listener: colocar modelo (tap) y moverlo (drag)
    private void configurarTouchListener() {
        binding.arSceneView.setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartX = x;
                    dragStartY = y;
                    estaArrastrando = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (modeloColocado) {
                        float dx = x - dragStartX;
                        float dy = y - dragStartY;
                        if (!estaArrastrando
                                && Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD_PX) {
                            estaArrastrando = true;
                        }
                        if (estaArrastrando) {
                            long ahora = System.currentTimeMillis();
                            if (ahora - ultimoMoveMs > MOVE_INTERVALO_MS) {
                                ultimoMoveMs = ahora;
                                moverModeloA(x, y);
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (!modeloColocado && lastFrame != null
                            && modoActual == Modo.ESCANEANDO && !estaArrastrando) {
                        procesarToque(x, y);
                    }
                    estaArrastrando = false;
                    break;
            }
            return true;
        });
    }

    //Mover modelo arrastrando
    private void moverModeloA(float x, float y) {
        if (lastFrame == null || modelNodeActual == null) return;
        List<HitResult> hits = lastFrame.hitTest(x, y);
        for (HitResult hit : hits) {
            Trackable t = hit.getTrackable();
            if (!(t instanceof Plane) || !((Plane) t).isPoseInPolygon(hit.getHitPose())) continue;
            try {
                Anchor nuevoAnchor = hit.createAnchor();
                AnchorNode nuevoNodo = new AnchorNode(
                        binding.arSceneView.getEngine(), nuevoAnchor, null, null, null, null);
                binding.arSceneView.addChildNode(nuevoNodo);
                anchorActual.removeChildNode(modelNodeActual);
                nuevoNodo.addChildNode(modelNodeActual);
                binding.arSceneView.removeChildNode(anchorActual);
                anchorActual.destroy();
                if (anchorARCore != null) anchorARCore.detach();
                anchorActual = nuevoNodo;
                anchorARCore = nuevoAnchor;
            } catch (Exception e) {
                Log.w(TAG, "Error moviendo modelo", e);
            }
            break;
        }
    }

    //Carga inicial: productos + modelo TFLite
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
                    if (p.getVectoresIA() != null && !p.getVectoresIA().isEmpty()) {
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

    //Transición de modo
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

    //hitTest + colocación de modelo
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
            binding.layoutHintSuperficie.setVisibility(View.GONE);

            if (productoActual != null) {
                runOnUiThread(() -> mostrarInfoProducto(productoActual));
            } else {
                runOnUiThread(() -> {
                    binding.tvEstadoAR.setText("MODELO COLOCADO");
                    binding.tvInstruccion.setText("Arrastra para mover · Toca 'Cerrar' para reposicionar");
                });
            }
            modeloColocado = true;
        } catch (Exception e) {
            Log.e(TAG, "Error al crear anchor por hitTest", e);
        }
    }

    //Carga del modelo 3D
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
                        modelNodeActual = new ModelNode(
                                modelInstance, true,
                                MODELO_ESCALA_METROS,
                                new Float3(0f, 0f, 0f));
                        runOnUiThread(() -> anchorActual.addChildNode(modelNodeActual));
                    }
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    //UI del panel inferior
    private void mostrarInfoProducto(Producto p) {
        etiquetaYaAnimada = false;
        mostrarEtiqueta(p);
        binding.tvEstadoAR.setText("PRODUCTO DETECTADO");
        binding.tvInstruccion.setText(p.getNombre());
        binding.tvProductoNombreAR.setText(p.getNombre());
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
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(binding.ivProductoThumbAR);
            binding.ivProductoThumbAR.setVisibility(View.VISIBLE);
        } else {
            binding.ivProductoThumbAR.setVisibility(View.GONE);
        }

        // Mostrar chevron para expandir/contraer
        binding.btnMinimizarPanel.setVisibility(View.VISIBLE);
        binding.btnMinimizarPanel.setRotation(270f);

        // Card empieza minimizada: solo se ve la barra de estado
        panelMinimizado = true;
        binding.tvInstruccion.setVisibility(View.GONE);
        binding.layoutInfoProducto.setVisibility(View.GONE);
    }

    private void mostrarEtiqueta(Producto p) {
        binding.tvEtiquetaNombre.setText(p.getNombre());
        binding.tvEtiquetaPrecio.setText(String.format(Locale.US, "$%.2f", p.getPrecio()));
    }

    private void proyectarEtiqueta(Frame frame, Anchor anchor) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) {
            runOnUiThread(() -> {
                binding.layoutEtiquetaFlotante.setVisibility(View.GONE);
                binding.ivIndicadorOffscreen.setVisibility(View.GONE);
            });
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
            runOnUiThread(() -> {
                binding.layoutEtiquetaFlotante.setVisibility(View.GONE);
                binding.ivIndicadorOffscreen.setVisibility(View.GONE);
            });
            return;
        }

        float[] clip = new float[4];
        Matrix.multiplyMV(clip, 0, projMatrix, 0, mv, 0);

        float ndcX = clip[0] / clip[3];
        float ndcY = clip[1] / clip[3];

        if (Math.abs(ndcX) > 1.0f || Math.abs(ndcY) > 1.0f) {
            // Objeto fuera de cámara: mostrar indicador en el borde
            final float fnx = ndcX, fny = ndcY;
            runOnUiThread(() -> {
                binding.layoutEtiquetaFlotante.setVisibility(View.GONE);
                actualizarIndicadorOffscreen(fnx, fny);
            });
            return;
        }

        int screenW = binding.arSceneView.getWidth();
        int screenH = binding.arSceneView.getHeight();
        if (screenW == 0 || screenH == 0) return;

        float screenX = (ndcX + 1f) * 0.5f * screenW;
        float screenY = (1f - ndcY) * 0.5f * screenH;

        runOnUiThread(() -> {
            binding.ivIndicadorOffscreen.setVisibility(View.GONE);
            binding.layoutEtiquetaFlotante.setVisibility(View.VISIBLE);
            binding.layoutEtiquetaFlotante.setTranslationX(
                    screenX - binding.layoutEtiquetaFlotante.getWidth() / 2f);
            binding.layoutEtiquetaFlotante.setTranslationY(
                    screenY - binding.layoutEtiquetaFlotante.getHeight() / 2f);

            if (!etiquetaYaAnimada) {
                etiquetaYaAnimada = true;
            }
        });
    }

    //Indicador off-scree
    private void actualizarIndicadorOffscreen(float ndcX, float ndcY) {
        int W = binding.arSceneView.getWidth();
        int H = binding.arSceneView.getHeight();
        if (W == 0 || H == 0) return;

        // Ángulo hacia el objeto (NDC: Y crece hacia arriba, pantalla Y crece hacia abajo)
        double angulo = Math.atan2(-ndcY, ndcX);

        // Rotar el ícono de flecha para que apunte en esa dirección
        binding.ivIndicadorOffscreen.setRotation((float) Math.toDegrees(angulo));

        // Posicionar en el borde de la pantalla con margen de 24px
        int margen = 40;
        float cos = (float) Math.cos(angulo);
        float sin = (float) Math.sin(angulo);

        // Escalar hasta el borde más cercano
        float escalaX = (W / 2f - margen) / Math.max(Math.abs(cos * W / 2f), 1f);
        float escalaY = (H / 2f - margen) / Math.max(Math.abs(sin * H / 2f), 1f);
        float escala = Math.min(escalaX, escalaY);

        float px = W / 2f + cos * Math.abs(cos) * (W / 2f - margen);
        float py = H / 2f - sin * Math.abs(sin) * (H / 2f - margen);

        binding.ivIndicadorOffscreen.setTranslationX(px - 16f);
        binding.ivIndicadorOffscreen.setTranslationY(py - 16f);
        binding.ivIndicadorOffscreen.setVisibility(View.VISIBLE);
    }

    private void ocultarInfoProducto() {
        binding.layoutInfoProducto.setVisibility(View.GONE);
        binding.layoutHintSuperficie.setVisibility(View.GONE);
        binding.ivIndicadorOffscreen.setVisibility(View.GONE);
        binding.layoutEtiquetaFlotante.setVisibility(View.GONE);
        binding.btnMinimizarPanel.setVisibility(View.GONE);
        binding.tvInstruccion.setVisibility(View.VISIBLE);

        if (anchorActual != null) {
            binding.arSceneView.removeChildNode(anchorActual);
            anchorActual.destroy();
            anchorActual = null;
        }
        anchorARCore = null;
        modelNodeActual = null;
        modeloColocado = false;
        etiquetaYaAnimada = false;
        panelMinimizado = false;

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
    }

    private void abrirFichaProducto() {
        if (productoIdActual == null) return;
        Intent intent = new Intent(this, DetalleProductoActivity.class);
        intent.putExtra("idProducto", productoIdActual);
        startActivity(intent);
    }

    //Lifecycle
    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        modelNodeActual = null;
    }
}
