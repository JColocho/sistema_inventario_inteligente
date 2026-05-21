package com.example.sistema_inventario_inteligente;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.sistema_inventario_inteligente.models.Categoria;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class AgregarProductoActivity extends AppCompatActivity {
    public ImageView btnAtras, imgProducto, btnQuitarModelo;
    public Button btnGuardarGaleria, btnGuardarCamara, btnSeleccionarModelo , btnGuardarProducto;
    public EditText txtNombre, txtDescripcion, txtPrecio,
            txtCantidad;
    public String rutaCamara;
    private Uri uriImageCamara;
    private Uri uriModeloSelccionado = null;
    private Uri uriFotoSeleccionada = null;
    private String urlImagenProducto = "";
    private String urlModelo3D = "";
    public ArrayList<Categoria> listaCategorias;
    public ArrayAdapter<Categoria> adapterCategoria;
    public Spinner spTiendas, spCategoria;
    private ProductoContrato repositorio = new ProductoRepository();
    public LinearLayout layoutModeloSeleccionado;
    public TextView txtNombreModelo;

    private final ActivityResultLauncher<PickVisualMediaRequest> selecionarImagen =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri ->{
                if (uri != null){
                    uriFotoSeleccionada = uri;
                    imgProducto.setImageURI(uri);
                }
            });
    private final ActivityResultLauncher<Uri> seleccionaImagenCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(),resultado-> {
                if(resultado!=null){
                    uriFotoSeleccionada = uriImageCamara;
                    imgProducto.setImageURI(uriImageCamara);
                }else {
                    Toast.makeText(this, "No se tomo la foto", Toast.LENGTH_SHORT).show();
                }
            });
    private final ActivityResultLauncher<String[]> selectorModelo =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    // Persiste el permiso para poder leerlo después
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                    uriModeloSelccionado = uri;
                    mostrarModeloSeleccionado(uri);
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
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, imeInsets.bottom)
            );
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
        btnGuardarGaleria = findViewById(R.id.btnGaleriaAddProducto);
        btnGuardarProducto = findViewById(R.id.btnGuardarProducto);

        listaCategorias = new ArrayList<>();
        adapterCategoria = new ArrayAdapter<>(this, R.layout.spinner_item, listaCategorias);
        adapterCategoria.setDropDownViewResource(R.layout.spinner_dropdown);

        cargarCategorias();

        spCategoria.setAdapter(adapterCategoria);


        btnAtras.setOnClickListener(v -> {
            finish();
        });

        btnSeleccionarModelo.setOnClickListener(v -> {
            selectorModelo.launch(new String[]{"*/*"});
        });

        btnQuitarModelo.setOnClickListener(v -> {
            uriModeloSelccionado = null;
            layoutModeloSeleccionado.setVisibility(View.GONE);
        });

        btnGuardarGaleria.setOnClickListener(v -> {abrirGaleria();});
        btnGuardarCamara.setOnClickListener(v -> {validarPermisoCamara();});

        btnGuardarProducto.setOnClickListener(v -> {
            btnGuardarProducto.setEnabled(false);
            guardarProducto();
        });
    }
    //Metodo para validar los campos
    private boolean validarCampos(){

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

        if (txtPrecio.getText().toString().trim().isEmpty() ||
                Double.parseDouble(txtPrecio.getText().toString().trim()) <= 0){
            txtPrecio.setError("Precio invalido.");
            return false;
        }

        if (txtCantidad.getText().toString().trim().isEmpty() ||
                Double.parseDouble(txtCantidad.getText().toString()) <= 0){
            txtCantidad.setError("Cantidad invalido.");
            return false;
        }

        return true;
    }

    private void cargarCategorias(){
        DatabaseReference categoriaRef = FirebaseDatabase.getInstance().getReference("Categorias");

        categoriaRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaCategorias.clear();
                for (DataSnapshot dataSnapshot: snapshot.getChildren()){
                    Categoria categoria = dataSnapshot.getValue(Categoria.class);
                    listaCategorias.add(categoria);
                    adapterCategoria.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private void abrirGaleria(){

        selecionarImagen.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());


    }

    private final ActivityResultLauncher<String> seleccionaImagenCamara2 =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),resultado-> {
                if (resultado) {
                    abrirCamara();
                } else {


                    Toast.makeText(
                            this,
                            "Permiso de cámara denegado",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

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


        if (!directorio.exists()) {
            directorio.mkdirs();
        }


        return new File(directorio, nombreArchivo);
    }

    private void validarPermisoCamara() {


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {


            abrirCamara();


        } else {


            seleccionaImagenCamara2.launch(
                    Manifest.permission.CAMERA
            );
        }
    }
    private void mostrarModeloSeleccionado(Uri uri) {
        // Obtiene el nombre real del archivo
        String nombreArchivo = "modelo.glb"; // fallback
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index >= 0) nombreArchivo = cursor.getString(index);
            cursor.close();
        }

        txtNombreModelo.setText(nombreArchivo);
        layoutModeloSeleccionado.setVisibility(View.VISIBLE);
    }
    private void guardarProducto(){
        try {
            //Validando los campos
            if (validarCampos()){
                //Capturando los datos de los campos
                String nombre = txtNombre.getText().toString().trim().toUpperCase(),
                        descripcion = txtDescripcion.getText().toString().trim().toUpperCase(),
                        categoria = spCategoria.getSelectedItem().toString();

                double precio = Double.parseDouble(txtPrecio.getText().toString()),
                        cantidad = Double.parseDouble(txtCantidad.getText().toString());

                //Validando que se haya seleccionado una imagen de referencia
                //del producto
                if (uriFotoSeleccionada != null){


                    //Subiendo la imagen al storage
                    repositorio.subirImagenStorage(uriFotoSeleccionada, new ProductoContrato.StorageCallBack() {
                        @Override
                        public void onExito(String urlDescarga) {
                            //Obteniendo la URL de la imagen
                            urlImagenProducto = urlDescarga;

                            //Validando que se haya seleccionado el modelo 3D del producto
                            if (uriModeloSelccionado != null){

                                //Subiendo el modelo 3D al storage
                                repositorio.subirModelo3DStorage(uriModeloSelccionado, new ProductoContrato.StorageCallBack() {
                                    @Override
                                    public void onExito(String urlDescarga) {
                                        //Obtenemos la URL del modelo 3D
                                        urlModelo3D = urlDescarga;

                                        //Capturamos todos lo datos del producto en el objeto
                                        Producto producto = new Producto(urlImagenProducto, urlModelo3D,nombre, categoria, descripcion,
                                                precio, cantidad);

                                        //Hacemos la inserción en el repositorio
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
                                    }
                                });
                            }
                            else {
                                Toast.makeText(AgregarProductoActivity.this, "No se ha seleccionado un modelo 3D.", Toast.LENGTH_SHORT).show();
                            }

                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(AgregarProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }else {
                    Toast.makeText(this, "No se ha seleccionado una imagen.", Toast.LENGTH_SHORT).show();
                }
            }

        } catch (Exception e) {

            Log.i("ERROR DB", "No se pudo subir los datos");
            throw new RuntimeException(e);
        }
    }
}