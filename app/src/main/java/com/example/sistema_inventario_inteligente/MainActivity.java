package com.example.sistema_inventario_inteligente;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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

        navigationView = findViewById(R.id.navegationView);
        frameVista = findViewById(R.id.containerMain);

        loadFragment(new InicioFragment());

        navigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.inicio) {
                loadFragment(new InicioFragment());
                return true;
            } else if (item.getItemId() == R.id.invetario) {
                loadFragment(new InventarioFragment());
                return true;
            } else if (item.getItemId() == R.id.sucursal) {
                loadFragment(new SucursalFragment());
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.containerMain, fragment)
                .commit();
    }
}
