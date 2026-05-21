package com.example.sistema_inventario_inteligente;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.sistema_inventario_inteligente.ar.EmbeddingHelper;
import com.example.sistema_inventario_inteligente.glide.GlideApp;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.example.sistema_inventario_inteligente.models.SucursalContrato;
import com.example.sistema_inventario_inteligente.models.SucursalRepository;
import com.example.sistema_inventario_inteligente.models.VectoresIA;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditarProductoActivity extends AppCompatActivity {

    private static final String TAG = "EditarProducto";

    private static final String[] CATEGORIAS = {
            "Computadoras", "Smartphones", "Tablets", "Televisores", "Audio", "Perifericos", "Otro"
    };

    // Views
    public ImageView btnAtras, imgProducto, btnQuitarModelo;
    public Button btnGuardarCamara, btnSeleccionarModelo, btnActualizarProducto;
    public EditText txtNombre, txtDescripcion, txtPrecio, txtCantidad;
    public Spinner spCategoria, spSucursalEdit;
    public LinearLayout layoutModeloSeleccionado;
    public TextView txtNombreModelo;

    // Estado interno
    private String idProducto = "";
    private Producto productoActual = null;
    private Producto productoNuevo = null;
    private Uri uriImageCamara;
    private Uri uriModeloSeleccionado = null;
    private Uri uriFotoSeleccionada = null;
    private String rutaCamara;
    private final List<Sucursal> listaSucursales = new ArrayList<>();

    // Repositorios + IA
    private final ProductoContrato repositorio = new ProductoRepository();
    private final SucursalContrato repositorioSucursal = new SucursalRepository();
    private EmbeddingHelper embeddingHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Uri> seleccionaImagenCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), resultado -> {
                if (resultado != null && resultado) {
                    uriFotoSeleccionada = uriImageCamara;
                    imgProducto.setImageURI(uriImageCamara);
                } else {
                    Toast.makeText(this, "No se tomó la foto", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> permisosCamara =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), resultado -> {
                if (resultado) abrirCamara();
                else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String[]> selectorModelo =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    uriModeloSeleccionado = uri;
                    mostrarModeloSeleccionado(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_editar_producto);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets  = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, imeInsets.bottom));
            return insets;
        });

        idProducto = getIntent().getStringExtra("idProducto");
        if (idProducto == null || idProducto.isEmpty()) {
            Toast.makeText(this, "Error: no se recibió el ID del producto.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgProducto              = findViewById(R.id.imgProductoEdit);
        layoutModeloSeleccionado = findViewById(R.id.layoutModeloSeleccionado);
        txtNombreModelo          = findViewById(R.id.txtNombreModelo);
        txtNombre                = findViewById(R.id.txtNombreProductoEdit);
        txtDescripcion           = findViewById(R.id.txtDescripcionEdit);
        txtPrecio                = findViewById(R.id.txtPrecioEdit);
        txtCantidad              = findViewById(R.id.txtCantidadEdit);
        spCategoria              = findViewById(R.id.spCategoriaEdit);
        spSucursalEdit           = findViewById(R.id.spSucursalEdit);
        btnAtras                 = findViewById(R.id.btnEditarBack);
        btnGuardarCamara         = findViewById(R.id.btnCamaraEditarProducto);
        btnSeleccionarModelo     = findViewById(R.id.btnSeleccionarModelo);
        btnQuitarModelo          = findViewById(R.id.btnQuitarModelo);
        btnActualizarProducto    = findViewById(R.id.btnEditarProducto);

        configurarSpinnerCategorias();
        cargarSucursales();
        configurarListeners();
        cargarDatosProducto();

        executor.execute(() -> {
            try {
                embeddingHelper = new EmbeddingHelper(this);
            } catch (Throwable t) {
                Log.e(TAG, "No se pudo cargar el modelo de embedding", t);
            }
        });
    }

    private void configurarSpinnerCategorias() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, CATEGORIAS);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown);
        spCategoria.setAdapter(adapter);
    }

    private void configurarListeners() {
        btnAtras.setOnClickListener(v -> finish());
        btnGuardarCamara.setOnClickListener(v -> validarPermisoCamara());
        btnSeleccionarModelo.setOnClickListener(v -> selectorModelo.launch(new String[]{"*/*"}));
        btnQuitarModelo.setOnClickListener(v -> {
            uriModeloSeleccionado = null;
            layoutModeloSeleccionado.setVisibility(View.GONE);
        });
        btnActualizarProducto.setOnClickListener(v -> actualizarProducto());
    }

    private void cargarDatosProducto() {
        repositorio.obtenerProductoId(idProducto, new ProductoContrato.LeerIdCallback() {
            @Override
            public void onProductoCargado(Producto producto) {
                if (producto == null) {
                    Toast.makeText(EditarProductoActivity.this,
                            "No se encontró el producto.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                productoActual = producto;
                txtNombre.setText(producto.getNombre());
                txtDescripcion.setText(producto.getDescripcion());
                txtPrecio.setText(String.valueOf(producto.getPrecio()));
                txtCantidad.setText(String.valueOf(producto.getCantidad()));

                StorageReference imageRef = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(producto.getUrlImagenProducto());
                GlideApp.with(EditarProductoActivity.this)
                        .load(imageRef)
                        .placeholder(R.drawable.camara)
                        .error(R.drawable.camara)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .centerCrop()
                        .into(imgProducto);

                if (producto.getUrlModelo3D() != null && !producto.getUrlModelo3D().isEmpty()) {
                    txtNombreModelo.setText("modelo_actual.glb");
                    layoutModeloSeleccionado.setVisibility(View.VISIBLE);
                }
                seleccionarCategoriaEnSpinner(producto.getCategoria());
                seleccionarSucursalEnSpinner(producto.getIdSucursal());
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void seleccionarCategoriaEnSpinner(String nombreCategoria) {
        if (nombreCategoria == null) return;
        for (int i = 0; i < CATEGORIAS.length; i++) {
            if (CATEGORIAS[i].equalsIgnoreCase(nombreCategoria)) {
                spCategoria.setSelection(i);
                return;
            }
        }
    }

    private void cargarSucursales() {
        repositorioSucursal.obtenerTodas(new SucursalContrato.LeerCallback() {
            @Override
            public void onSucursalesCargadas(List<Sucursal> sucursales) {
                listaSucursales.clear();
                listaSucursales.addAll(sucursales);
                ArrayAdapter<Sucursal> adapter = new ArrayAdapter<>(
                        EditarProductoActivity.this, R.layout.spinner_item, listaSucursales);
                adapter.setDropDownViewResource(R.layout.spinner_dropdown);
                spSucursalEdit.setAdapter(adapter);
                if (productoActual != null)
                    seleccionarSucursalEnSpinner(productoActual.getIdSucursal());
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void seleccionarSucursalEnSpinner(String idSucursal) {
        if (idSucursal == null) return;
        for (int i = 0; i < listaSucursales.size(); i++) {
            if (idSucursal.equals(listaSucursales.get(i).getIdSucursal())) {
                spSucursalEdit.setSelection(i);
                return;
            }
        }
    }

    private boolean validarCampos() {
        if (txtNombre.getText().toString().trim().isEmpty()) {
            txtNombre.setText("");
            txtNombre.setError("Campo vacio.");
            return false;
        }
        if (txtDescripcion.getText().toString().trim().isEmpty()) {
            txtDescripcion.setText("");
            txtDescripcion.setError("Campo vacio.");
            return false;
        }
        if (txtPrecio.getText().toString().trim().isEmpty()) {
            txtPrecio.setError("Precio invalido.");
            return false;
        }
        if (txtCantidad.getText().toString().trim().isEmpty()) {
            txtCantidad.setError("Cantidad invalido.");
            return false;
        }
        return true;
    }

    private void actualizarProducto() {
        if (productoActual == null) {
            Toast.makeText(this, "Espera a que carguen los datos del producto.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validarCampos()) return;

        if (listaSucursales.isEmpty()) {
            Toast.makeText(this, "No hay sucursales disponibles.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnActualizarProducto.setEnabled(false);

        String idSucursal = listaSucursales.get(spSucursalEdit.getSelectedItemPosition()).getIdSucursal();

        productoNuevo = new Producto(
                productoActual.getUrlImagenProducto(),
                productoActual.getUrlModelo3D(),
                txtNombre.getText().toString().trim().toUpperCase(),
                spCategoria.getSelectedItem().toString(),
                txtDescripcion.getText().toString().trim().toUpperCase(),
                Double.parseDouble(txtPrecio.getText().toString().trim()),
                Double.parseDouble(txtCantidad.getText().toString().trim()));
        productoNuevo.setIdProducto(productoActual.getIdProducto());
        productoNuevo.setIdSucursal(idSucursal);
        productoNuevo.setVectoresIA(productoActual.getVectoresIA());

        if (uriFotoSeleccionada != null) {
            // Recalcular embedding antes de subir la nueva imagen
            executor.execute(() -> {
                VectoresIA vectoresIA = null;
                if (embeddingHelper != null) {
                    try {
                        Bitmap bmp = decodificarBitmap(uriFotoSeleccionada);
                        float[] vector = embeddingHelper.extraer(bmp);
                        ArrayList<Double> lista = new ArrayList<>();
                        for (float f : vector) lista.add((double) f);
                        vectoresIA = new VectoresIA(lista);
                    } catch (Exception e) {
                        Log.e(TAG, "Error al extraer embedding", e);
                    }
                }
                VectoresIA vFinal = vectoresIA;
                runOnUiThread(() -> subirImagenYContinuar(vFinal));
            });
        } else if (uriModeloSeleccionado != null) {
            subirModeloYContinuar();
        } else {
            guardarSiCambio();
        }
    }

    private void subirImagenYContinuar(VectoresIA vectoresIA) {
        repositorio.actualizarImagenStorage(productoActual.getUrlImagenProducto(),
                uriFotoSeleccionada, new ProductoContrato.StorageCallBack() {
            @Override
            public void onExito(String url) {
                productoNuevo.setUrlImagenProducto(url);
                if (vectoresIA != null) productoNuevo.setVectoresIA(vectoresIA);
                if (uriModeloSeleccionado != null) {
                    subirModeloYContinuar();
                } else {
                    guardarSiCambio();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                btnActualizarProducto.setEnabled(true);
            }
        });
    }

    private void subirModeloYContinuar() {
        String urlActual = productoActual.getUrlModelo3D();
        ProductoContrato.StorageCallBack cb = new ProductoContrato.StorageCallBack() {
            @Override
            public void onExito(String url) {
                productoNuevo.setUrlModelo3D(url);
                guardarSiCambio();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                btnActualizarProducto.setEnabled(true);
            }
        };

        if (urlActual != null && !urlActual.isEmpty()) {
            repositorio.actualizarModelo3DStorage(urlActual, uriModeloSeleccionado, cb);
        } else {
            repositorio.subirModelo3DStorage(uriModeloSeleccionado, cb);
        }
    }

    private void guardarSiCambio() {
        if (!productoNuevo.equals(productoActual)) {
            repositorio.actualizarProducto(productoNuevo, new ProductoContrato.OperacionCallback() {
                @Override
                public void onExito(String mensaje) {
                    Toast.makeText(EditarProductoActivity.this, mensaje, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                    btnActualizarProducto.setEnabled(true);
                }
            });
        } else {
            finish();
        }
    }
    private void validarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamara();
        } else {
            permisosCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirCamara() {
        try {
            File archivoImagen = crearArchivoImagenCamara();
            rutaCamara = archivoImagen.getAbsolutePath();
            uriImageCamara = FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", archivoImagen);
            seleccionaImagenCamara.launch(uriImageCamara);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al crear imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private File crearArchivoImagenCamara() throws IOException {
        String nombreArchivo = "CAM_" + System.currentTimeMillis() + ".jpg";
        File directorio = new File(getFilesDir(), "imagenes");
        if (!directorio.exists()) directorio.mkdirs();
        return new File(directorio, nombreArchivo);
    }

    private void mostrarModeloSeleccionado(Uri uri) {
        String nombreArchivo = "modelo.glb";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index >= 0) nombreArchivo = cursor.getString(index);
            cursor.close();
        }
        txtNombreModelo.setText(nombreArchivo);
        layoutModeloSeleccionado.setVisibility(View.VISIBLE);
    }

    private Bitmap decodificarBitmap(Uri uri) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(is);
        if (is != null) is.close();

        InputStream is2 = getContentResolver().openInputStream(uri);
        ExifInterface exif = new ExifInterface(is2);
        if (is2 != null) is2.close();

        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  matrix.postRotate(90);  break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (embeddingHelper != null) embeddingHelper.close();
        executor.shutdownNow();
    }
}
