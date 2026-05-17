package com.example.sistema_inventario_inteligente;

import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.sistema_inventario_inteligente.models.Producto;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView navigationView;
    private FrameLayout frameVista;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        /*
        //Conexion a la base de datos
        //Obtener la instancia a la base de datos
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        //Crear la referencia a la tabla
        DatabaseReference productosRef = database.getReference("Productos");
        //Crear el objeto con todos los datos a guardar
        Producto producto = new Producto("ASUS", "Computadoras", "Ryzen 7 7000, RX7000", 800.00 ,5.0);

        //.push() crea un ID único aleatorio (ej. -NjsdH83jd9) para que no se dupliquen
        // y .setValue() inserta el objeto allí.
        String idProducto = productosRef.push().getKey();

        producto.idProducto = idProducto;

        productosRef.child(idProducto).setValue(producto);


         */



        navigationView = findViewById(R.id.navegationView);
        frameVista = findViewById(R.id.containerMain);

        loadFragment(new InicioFragment());

        //Manejo de fragment
        navigationView.setOnItemSelectedListener(item -> {
            if(item.getItemId() == R.id.inicio){
                loadFragment(new InicioFragment());

                Toast.makeText(this, "Inicio", Toast.LENGTH_SHORT).show();
                return true;
            }
            else if(item.getItemId() == R.id.invetario){
                loadFragment(new InventarioFragment());

                Toast.makeText(this, "Inventario", Toast.LENGTH_SHORT).show();
                return true;


            } else if (item.getItemId() == R.id.sucursal) {
                loadFragment(new SucursalFragment());

                Toast.makeText(this, "Sucursal", Toast.LENGTH_SHORT).show();
                return true;
            }

            return false;
        });



    }
    //Metodo para mostrar vista seleccionada
    private void loadFragment(Fragment fragment){
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.containerMain, fragment)
                .commit();
    }
}