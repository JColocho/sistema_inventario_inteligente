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

import com.example.sistema_inventario_inteligente.ar.EmbeddingHelper;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.example.sistema_inventario_inteligente.models.SucursalContrato;
import com.example.sistema_inventario_inteligente.models.SucursalRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AgregarProductoActivity extends AppCompatActivity {

    private static final String[] CATEGORIAS = {
            "Computadoras", "Smartphones", "Tablets", "Televisores", "Audio", "Perifericos", "Otro"
    };

    public ImageView btnAtras, imgProducto, btnQuitarModelo;
    public Button btnGuardarCamara, btnSeleccionarModelo, btnGuardarProducto;
    public EditText txtNombre, txtDescripcion, txtPrecio, txtCantidad;
    public String rutaCamara;
    private Uri uriImageCamara;
    private Uri uriModeloSelccionado = null;
    private Uri uriFotoSeleccionada = null;
    private String urlImagenProducto = "";
    private String urlModelo3D = "";
    public Spinner spCategoria, spSucursalAdd;
    private ProductoContrato repositorio = new ProductoRepository();
    private final SucursalContrato repositorioSucursal = new SucursalRepository();
    private final List<Sucursal> listaSucursales = new ArrayList<>();
    public LinearLayout layoutModeloSeleccionado;
    public TextView txtNombreModelo;

    private EmbeddingHelper embeddingHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Uri> seleccionaImagenCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), resultado -> {
                if (resultado != null) {
                    uriFotoSeleccionada = uriImageCamara;
                    imgProducto.setImageURI(uriImageCamara);
                } else {
                    Toast.makeText(this, "No se tomo la foto", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> selectorModelo =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    uriModeloSelccionado = uri;
                    mostrarModeloSeleccionado(uri);
                }
            });

    private final ActivityResultLauncher<String> seleccionaImagenCamara2 =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), resultado -> {
                if (resultado) {
                    abrirCamara();
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_agregar_producto);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, imeInsets.bottom));
            return insets;
        });

        imgProducto = findViewById(R.id.imgProductoRef);
        layoutModeloSeleccionado = findViewById(R.id.layoutModeloSeleccionado);
        txtNombreModelo = findViewById(R.id.txtNombreModelo);
        txtNombre = findViewById(R.id.txtNombreProductoAdd);
        txtDescripcion = findViewById(R.id.txtDescripcionAdd);
        txtPrecio = findViewById(R.id.txtPrecioAdd);
        txtCantidad = findViewById(R.id.txtCantidadAdd);
        spCategoria = findViewById(R.id.spCategoriaAdd);
        btnAtras = findViewById(R.id.btnAddBack);
        btnGuardarCamara = findViewById(R.id.btnCamaraAddProducto);
        btnSeleccionarModelo = findViewById(R.id.btnSeleccionarModelo);
        btnQuitarModelo = findViewById(R.id.btnQuitarModelo);
        btnGuardarProducto = findViewById(R.id.btnGuardarProducto);
        spSucursalAdd = findViewById(R.id.spSucursalAdd);

        ArrayAdapter<String> adapterCategoria = new ArrayAdapter<>(this, R.layout.spinner_item, CATEGORIAS);
        adapterCategoria.setDropDownViewResource(R.layout.spinner_dropdown);
        spCategoria.setAdapter(adapterCategoria);

        cargarSucursales();

        btnAtras.setOnClickListener(v -> finish());
        btnSeleccionarModelo.setOnClickListener(v -> selectorModelo.launch(new String[]{"*/*"}));
        btnQuitarModelo.setOnClickListener(v -> {
            uriModeloSelccionado = null;
            layoutModeloSeleccionado.setVisibility(View.GONE);
        });
        btnGuardarCamara.setOnClickListener(v -> validarPermisoCamara());
        btnGuardarProducto.setOnClickListener(v -> {
            btnGuardarProducto.setEnabled(false);
            guardarProducto();
        });

        // Cargar el modelo LiteRT en segundo plano para no bloquear la UI
        executor.execute(() -> {
            try {
                embeddingHelper = new EmbeddingHelper(this);
            } catch (Throwable t) {
                Log.e("EmbeddingHelper", "No se pudo cargar el modelo", t);
            }
        });
    }

    private void cargarSucursales() {
        repositorioSucursal.obtenerTodas(new SucursalContrato.LeerCallback() {
            @Override
            public void onSucursalesCargadas(List<Sucursal> sucursales) {
                listaSucursales.clear();
                listaSucursales.addAll(sucursales);
                ArrayAdapter<Sucursal> adapter = new ArrayAdapter<>(
                        AgregarProductoActivity.this, R.layout.spinner_item, listaSucursales);
                adapter.setDropDownViewResource(R.layout.spinner_dropdown);
                spSucursalAdd.setAdapter(adapter);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AgregarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
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
        if (txtPrecio.getText().toString().trim().isEmpty() ||
                Double.parseDouble(txtPrecio.getText().toString().trim()) <= 0) {
            txtPrecio.setError("Precio invalido.");
            return false;
        }
        if (txtCantidad.getText().toString().trim().isEmpty() ||
                Double.parseDouble(txtCantidad.getText().toString()) <= 0) {
            txtCantidad.setError("Cantidad invalido.");
            return false;
        }
        return true;
    }

    private void abrirCamara() {
        try {
            File archivoImagen = crearArchivoImagenCamara();
            rutaCamara = archivoImagen.getAbsolutePath();
            uriImageCamara = FileProvider.getUriForFile(this, getPackageName() + ".provider", archivoImagen);
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

    private void validarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamara();
        } else {
            seleccionaImagenCamara2.launch(Manifest.permission.CAMERA);
        }
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

    private void guardarProducto() {
        if (!validarCampos()) {
            btnGuardarProducto.setEnabled(true);
            return;
        }

        if (uriFotoSeleccionada == null) {
            Toast.makeText(this, "No se ha seleccionado una imagen.", Toast.LENGTH_SHORT).show();
            btnGuardarProducto.setEnabled(true);
            return;
        }

        if (listaSucursales.isEmpty()) {
            Toast.makeText(this, "No hay sucursales disponibles.", Toast.LENGTH_SHORT).show();
            btnGuardarProducto.setEnabled(true);
            return;
        }

        String nombre      = txtNombre.getText().toString().trim().toUpperCase();
        String descripcion = txtDescripcion.getText().toString().trim().toUpperCase();
        String categoria   = spCategoria.getSelectedItem().toString();
        String idSucursal  = listaSucursales.get(spSucursalAdd.getSelectedItemPosition()).getIdSucursal();
        double precio      = Double.parseDouble(txtPrecio.getText().toString());
        double cantidad    = Double.parseDouble(txtCantidad.getText().toString());

        // Extraer embedding en hilo de fondo, luego subir a Firebase
        executor.execute(() -> {
            List<Double> vectoresIA = null;
            if (embeddingHelper != null) {
                try {
                    Bitmap bmp = decodificarBitmap(uriFotoSeleccionada);
                    float[] vector = embeddingHelper.extraer(bmp);
                    ArrayList<Double> listaVector = new ArrayList<>();
                    for (float f : vector) listaVector.add((double) f);
                    vectoresIA = listaVector;
                } catch (Exception e) {
                    Log.e("Embedding", "Error al extraer vector", e);
                }
            }
            List<Double> vectoresFinal = vectoresIA;
            runOnUiThread(() -> subirYGuardar(nombre, categoria, descripcion, precio, cantidad, idSucursal, vectoresFinal));
        });
    }

    private void subirYGuardar(String nombre, String categoria, String descripcion,
                                double precio, double cantidad, String idSucursal, List<Double> vectoresIA) {
        repositorio.subirImagenStorage(uriFotoSeleccionada, new ProductoContrato.StorageCallBack() {
            @Override
            public void onExito(String urlDescarga) {
                urlImagenProducto = urlDescarga;

                if (uriModeloSelccionado != null) {
                    repositorio.subirModelo3DStorage(uriModeloSelccionado, new ProductoContrato.StorageCallBack() {
                        @Override
                        public void onExito(String urlDescarga) {
                            urlModelo3D = urlDescarga;
                            Producto producto = new Producto(urlImagenProducto, urlModelo3D,
                                    nombre, categoria, descripcion, precio, cantidad);
                            producto.setIdSucursal(idSucursal);
                            producto.setVectoresIA(vectoresIA);
                            repositorio.insertarProducto(producto, new ProductoContrato.OperacionCallback() {
                                @Override
                                public void onExito(String mensaje) {
                                    Toast.makeText(AgregarProductoActivity.this, mensaje, Toast.LENGTH_SHORT).show();
                                    finish();
                                }

                                @Override
                                public void onError(String error) {
                                    Toast.makeText(AgregarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                                    btnGuardarProducto.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(AgregarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                            btnGuardarProducto.setEnabled(true);
                        }
                    });
                } else {
                    Toast.makeText(AgregarProductoActivity.this, "No se ha seleccionado un modelo 3D.", Toast.LENGTH_SHORT).show();
                    btnGuardarProducto.setEnabled(true);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AgregarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                btnGuardarProducto.setEnabled(true);
            }
        });
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
