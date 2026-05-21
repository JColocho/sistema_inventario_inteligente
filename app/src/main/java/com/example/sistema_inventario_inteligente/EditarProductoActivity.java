package com.example.sistema_inventario_inteligente;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.sistema_inventario_inteligente.glide.GlideApp;
import com.example.sistema_inventario_inteligente.models.Categoria;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class EditarProductoActivity extends AppCompatActivity {

    // Views
    public ImageView btnAtras, imgProducto, btnQuitarModelo;
    public Button btnGuardarGaleria, btnGuardarCamara, btnSeleccionarModelo, btnActualizarProducto;
    public EditText txtNombre, txtDescripcion, txtPrecio, txtCantidad;
    public Spinner spCategoria, spSucursal;
    public LinearLayout layoutModeloSeleccionado;
    public TextView txtNombreModelo;

    // Estado interno
    private String idProducto = "";
    private Producto productoActual, productoNuevo = null;
    private Uri uriImageCamara;
    private Uri uriModeloSeleccionado = null;
    private Uri uriFotoSeleccionada = null;
    private String rutaCamara;

    // Categorías
    public ArrayList<Categoria> listaCategorias;
    public ArrayList<Sucursal> listaSucursales;
    public ArrayAdapter<Categoria> adapterCategoria;
    public ArrayAdapter<Sucursal> adapterSucursal;

    // Repositorio
    private final ProductoContrato repositorio = new ProductoRepository();


    private final ActivityResultLauncher<PickVisualMediaRequest> selecionarImagen =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    uriFotoSeleccionada = uri;
                    imgProducto.setImageURI(uri);
                }
            });

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
                if (resultado) {
                    abrirCamara();
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> selectorModelo =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
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
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, imeInsets.bottom)
            );
            return insets;
        });

        // Obtener el ID del producto a editar
        idProducto = getIntent().getStringExtra("idProducto");
        if (idProducto == null || idProducto.isEmpty()) {
            Toast.makeText(this, "Error: no se recibió el ID del producto.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        imgProducto = findViewById(R.id.imgProductoEdit);
        layoutModeloSeleccionado = findViewById(R.id.layoutModeloSeleccionado);
        txtNombreModelo = findViewById(R.id.txtNombreModelo);
        txtNombre = findViewById(R.id.txtNombreProductoEdit);
        txtDescripcion = findViewById(R.id.txtDescripcionEdit);
        txtPrecio = findViewById(R.id.txtPrecioEdit);
        txtCantidad = findViewById(R.id.txtCantidadEdit);
        spCategoria = findViewById(R.id.spCategoriaEdit);
        spSucursal = findViewById(R.id.spSucursalEdit);
        btnAtras = findViewById(R.id.btnEditarBack);
        btnGuardarCamara = findViewById(R.id.btnCamaraEditarProducto);
        btnGuardarGaleria = findViewById(R.id.btnGaleriaEditarProducto);
        btnSeleccionarModelo = findViewById(R.id.btnSeleccionarModelo);
        btnQuitarModelo = findViewById(R.id.btnQuitarModelo);
        btnActualizarProducto = findViewById(R.id.btnEditarProducto);

        // Cargar datos del producto en los campos
        cargarDatosProducto();

        listaCategorias  = new ArrayList<>();
        adapterCategoria = new ArrayAdapter<>(this, R.layout.spinner_item, listaCategorias);
        adapterCategoria.setDropDownViewResource(R.layout.spinner_dropdown);
        spCategoria.setAdapter(adapterCategoria);
        cargarCategorias();

        listaSucursales  = new ArrayList<>();
        adapterSucursal = new ArrayAdapter<>(this, R.layout.spinner_item, listaSucursales);
        adapterSucursal.setDropDownViewResource(R.layout.spinner_dropdown);
        spSucursal.setAdapter(adapterSucursal);
        cargarSucursales();



        btnAtras.setOnClickListener(v -> finish());

        btnGuardarGaleria.setOnClickListener(v -> abrirGaleria());
        btnGuardarCamara.setOnClickListener(v -> validarPermisoCamara());

        btnSeleccionarModelo.setOnClickListener(v ->
                selectorModelo.launch(new String[]{"*/*"}));

        btnQuitarModelo.setOnClickListener(v -> {
            uriModeloSeleccionado = null;
            layoutModeloSeleccionado.setVisibility(View.GONE);
        });

        btnActualizarProducto.setOnClickListener(v -> {
            btnActualizarProducto.setEnabled(false);
            actualizarProducto();
        });
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

                // Campos de texto
                txtNombre.setText(producto.getNombre());
                txtDescripcion.setText(producto.getDescripcion());
                txtPrecio.setText(String.valueOf(producto.getPrecio()));
                txtCantidad.setText(String.valueOf(producto.getCantidad()));

                StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(producto.getUrlImagenProducto());

                // Imagen actual
                GlideApp.with(EditarProductoActivity.this)
                        .load(imageRef)
                        .placeholder(R.drawable.camara)
                        .error(R.drawable.camara)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .centerCrop()
                        .into(imgProducto);

                // Modelo 3D: mostrar indicador si ya tiene uno asignado
                if (producto.getUrlModelo3D() != null && !producto.getUrlModelo3D().isEmpty()) {
                    txtNombreModelo.setText("modelo_actual.glb");
                    layoutModeloSeleccionado.setVisibility(View.VISIBLE);
                }

                // Seleccionar la categoría en el spinner (puede que aún no hayan cargado)
                seleccionarCategoria(producto.getCategoria());
                seleccionarSucursal(producto.getIdSucursal());
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void cargarCategorias() {
        DatabaseReference categoriaRef = FirebaseDatabase.getInstance().getReference("Categorias");

        categoriaRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaCategorias.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Categoria categoria = ds.getValue(Categoria.class);
                    listaCategorias.add(categoria);
                }
                adapterCategoria.notifyDataSetChanged();

                // Una vez cargadas, intentar seleccionar la categoría del producto
                if (productoActual != null) {
                    seleccionarCategoria(productoActual.getCategoria());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditarProductoActivity.this,
                        "Error al cargar categorías.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarSucursales() {
        DatabaseReference sucursalRef = FirebaseDatabase.getInstance().getReference("sucursales");

        sucursalRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaSucursales.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Sucursal sucursal = ds.getValue(Sucursal.class);
                    sucursal.setIdSucursal(ds.getKey());
                    listaSucursales.add(sucursal);
                }
                adapterSucursal.notifyDataSetChanged();

                // Una vez cargadas, intentar seleccionar la categoría del producto
                if (productoActual != null) {
                    seleccionarSucursal(productoActual.getIdSucursal());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditarProductoActivity.this,
                        "Error al cargar categorías.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void seleccionarCategoria(String nombreCategoria) {
        if (nombreCategoria == null || listaCategorias.isEmpty()) return;
        for (int i = 0; i < listaCategorias.size(); i++) {
            if (listaCategorias.get(i).toString().equalsIgnoreCase(nombreCategoria)) {
                spCategoria.setSelection(i);
                return;
            }
        }
    }

    private void seleccionarSucursal(String idSucursal) {
        if (idSucursal == null || listaSucursales.isEmpty()) return;
        for (int i = 0; i < listaSucursales.size(); i++) {
            if (listaSucursales.get(i).getIdSucursal().equals(idSucursal)) {
                spSucursal.setSelection(i);
                return;
            }
        }
    }

    private boolean validarCampos() {
        if (txtNombre.getText().toString().trim().isEmpty()){
            txtNombre.setText("");
            txtNombre.setError("Campo vacio.");
            return false;
        }

        if (txtDescripcion.getText().toString().trim().isEmpty()){
            txtDescripcion.setText("");
            txtDescripcion.setError("Campo vacio.");
            return false;
        }

        if (txtPrecio.getText().toString().trim().isEmpty()){
            txtPrecio.setError("Precio invalido.");
            return false;
        }

        if (txtCantidad.getText().toString().trim().isEmpty()){
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

        btnActualizarProducto.setEnabled(false);

        Sucursal sucursal = (Sucursal) spSucursal.getSelectedItem();

        // Construimos el objeto con los datos del formulario,
        // manteniendo las URLs actuales como punto de partida
        productoNuevo = new Producto(
                productoActual.getUrlImagenProducto(),
                productoActual.getUrlModelo3D(),
                txtNombre.getText().toString().trim().toUpperCase(),
                spCategoria.getSelectedItem().toString(),
                txtDescripcion.getText().toString().trim().toUpperCase(),
                Double.parseDouble(txtPrecio.getText().toString().trim()),
                Double.parseDouble(txtCantidad.getText().toString().trim()),
                sucursal.getIdSucursal()
        );
        productoNuevo.setIdProducto(productoActual.getIdProducto());

        // PASO 1: actualizar imagen si cambió → luego modelo → luego guardar en DB
        if (uriFotoSeleccionada != null) {
            repositorio.actualizarImagenStorage(productoActual.getUrlImagenProducto(), uriFotoSeleccionada,
                    new ProductoContrato.StorageCallBack() {
                        @Override
                        public void onExito(String urlDescarga) {
                            // URL de imagen actualizada, continuamos con modelo
                            productoNuevo.setUrlImagenProducto(urlDescarga);
                            actualizarModeloYGuardar();
                        }
                        @Override
                        public void onError(String error) {
                            btnActualizarProducto.setEnabled(true);
                            Toast.makeText(EditarProductoActivity.this,
                                    "Error al subir imagen: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // No cambió la imagen, pasamos directo al modelo
            actualizarModeloYGuardar();
        }
    }
    private void actualizarModeloYGuardar() {
        if (uriModeloSeleccionado != null) {
            repositorio.actualizarModelo3DStorage(productoActual.getUrlModelo3D(), uriModeloSeleccionado,
                    new ProductoContrato.StorageCallBack() {
                        @Override
                        public void onExito(String urlDescarga) {
                            // URL de modelo actualizada, ahora sí guardamos en DB
                            productoNuevo.setUrlModelo3D(urlDescarga);
                            guardarEnDatabase();
                        }
                        @Override
                        public void onError(String error) {
                            btnActualizarProducto.setEnabled(true);
                            Toast.makeText(EditarProductoActivity.this,
                                    "Error al subir modelo: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // No cambió el modelo, guardamos directo en DB
            guardarEnDatabase();
        }
    }
    private void guardarEnDatabase() {
        repositorio.actualizarProducto(productoNuevo, new ProductoContrato.OperacionCallback() {
            @Override
            public void onExito(String mensaje) {
                Toast.makeText(EditarProductoActivity.this, mensaje, Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onError(String error) {
                btnActualizarProducto.setEnabled(true);
                Toast.makeText(EditarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void abrirGaleria() {
        selecionarImagen.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
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
                    this,
                    getPackageName() + ".provider",
                    archivoImagen
            );
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
}