package com.example.sistema_inventario_inteligente;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
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

import com.example.sistema_inventario_inteligente.models.Producto;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class AgregarProductoActivity extends AppCompatActivity {
    public ImageView btnAtras, imgProducto;
    public Button btnGuardarGaleria, btnGuardarCamara, btnGuardarProducto;
    public EditText txtNombre, txtDescripcion, txtPrecio,
            txtCantidad,txtCoordX, txtCoordY, txtCoordZ;
    public String rutaCamara;
    private Uri uriImageCamara;
    public Spinner spTiendas;

    private final ActivityResultLauncher<PickVisualMediaRequest> selecionarImagen =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri ->{
                if (uri != null){
                    String newRoute = copiarImagenApp(uri);
                    imgProducto.setImageURI(Uri.fromFile(new File(newRoute)));
                    //AQUI VAMOS A GUARDAR A LA BD
                    insertarImagen(newRoute);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_agregar_producto);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnAtras = findViewById(R.id.btnAddBack);
        imgProducto = findViewById(R.id.imgProductoRef);
        txtNombre = findViewById(R.id.txtNombreProductoAdd);
        txtDescripcion = findViewById(R.id.txtDescripcionAdd);
        txtPrecio = findViewById(R.id.txtPrecioAdd);
        txtCantidad = findViewById(R.id.txtCantidadAdd);
        txtCoordX = findViewById(R.id.txtCoordXAdd);
        txtCoordY = findViewById(R.id.txtCoordYAdd);
        txtCoordZ = findViewById(R.id.txtCoordZAdd);
        btnGuardarCamara = findViewById(R.id.btnCamaraAddProducto);
        btnGuardarGaleria = findViewById(R.id.btnGaleriaAddProducto);
        btnGuardarProducto = findViewById(R.id.btnGuardarProducto);

        btnAtras.setOnClickListener(v -> {
            finish();
        });

        btnGuardarGaleria.setOnClickListener(v -> {abrirGaleria();});
        btnGuardarCamara.setOnClickListener(v -> {validarPermisoCamara();});

        btnGuardarProducto.setOnClickListener(v -> {

            try {
                //Obtener la instancia a la base de datos
                FirebaseDatabase database = FirebaseDatabase.getInstance();

                //Crear la referencia a la tabla
                DatabaseReference productosRef = database.getReference("Productos");
                //Crear el objeto con todos los datos a guardar
                Producto producto = new Producto(txtNombre.getText().toString(),
                        "Computadoras",
                        txtDescripcion.getText().toString(),
                        Double.parseDouble(txtPrecio.getText().toString()),
                        Double.parseDouble(txtCantidad.getText().toString()),
                        Double.parseDouble(txtCoordX.getText().toString()),
                        Double.parseDouble(txtCoordY.getText().toString()),
                        Double.parseDouble(txtCoordZ.getText().toString()));

                //.push() crea un ID único aleatorio (ej. -NjsdH83jd9) para que no se dupliquen
                // y .setValue() inserta el objeto allí.
                String idProducto = productosRef.push().getKey();

                producto.setIdProducto(idProducto);

                productosRef.child(idProducto).setValue(producto);

                Toast.makeText(this, "Producto Registrado", Toast.LENGTH_SHORT).show();
                finish();
            } catch (Exception e) {

                Log.i("ERROR DB", "No se pudo subir los datos");
                throw new RuntimeException(e);
            }
        });
    }

    private void abrirGaleria(){

        selecionarImagen.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());


    }



    private String copiarImagenApp(Uri uriOriginal) {
        try {

            InputStream inputStream =
                    getContentResolver().openInputStream(uriOriginal);

            String nombreArchivo =
                    "IMG_" + System.currentTimeMillis() + ".jpg";

            File directorio =
                    new File(getFilesDir(), "imagenes");

            if (!directorio.exists()) {
                directorio.mkdirs();
            }

            File archivoDestino =
                    new File(directorio, nombreArchivo);

            OutputStream outputStream =
                    new FileOutputStream(archivoDestino);

            byte[] buffer = new byte[4096];
            int bytesLeidos;

            while ((bytesLeidos = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesLeidos);
            }

            inputStream.close();
            outputStream.close();

            return archivoDestino.getAbsolutePath();


        } catch (Exception e) {
            e.printStackTrace();
        }


        return null;
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

    private final ActivityResultLauncher<Uri> seleccionaImagenCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(),resultado-> {
                if(resultado!=null){
                    imgProducto.setImageURI(uriImageCamara);
                    insertarImagen(uriImageCamara.toString());


                }else {
                    Toast.makeText(this, "No se tomo la foto", Toast.LENGTH_SHORT).show();
                }
            });

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

    public void insertarImagen(String newRoute){
        //Insertar en db
    }
}