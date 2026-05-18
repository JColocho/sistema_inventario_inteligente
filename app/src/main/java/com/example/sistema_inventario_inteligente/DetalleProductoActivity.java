package com.example.sistema_inventario_inteligente;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DetalleProductoActivity extends AppCompatActivity {
    private String idProducto = "";
    public TextView txtNombre, txtDescripcion, txtCategoria;
    public ImageView imgProducto, btnCerrar, btnEliminar;
    private ProductoContrato productoRepositorio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detalle_producto);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        productoRepositorio = new ProductoRepository();

        imgProducto = findViewById(R.id.imgProductoDetalle);
        txtNombre = findViewById(R.id.txtNombreDetalle);
        txtDescripcion = findViewById(R.id.txtDescripcioDetalle);
        txtCategoria = findViewById(R.id.txtCategoriaDetalle);
        btnCerrar = findViewById(R.id.btnDetalleBack);
        btnEliminar = findViewById(R.id.btnElminarProducto);
        idProducto = getIntent().getStringExtra("idProducto");

        productoRepositorio.obtenerProductoId(idProducto, new ProductoContrato.LeerIdCallback() {
            @Override
            public void onProductoCargado(Producto productoObtenido) {
                Producto producto = productoObtenido;
                if (producto != null){
                    txtNombre.setText(producto.getNombre());
                    txtDescripcion.setText(producto.getDescripcion());
                    txtCategoria.setText(producto.getCategoria());

                    Glide.with(DetalleProductoActivity.this)
                            .load(producto.getUrlImagenProducto())
                            .placeholder(R.drawable.camara)
                            .error(R.drawable.camara)
                            .centerCrop()
                            .into(imgProducto);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DetalleProductoActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });

        btnCerrar.setOnClickListener(v -> {finish();});

        btnEliminar.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Eliminar producto")
                    .setMessage("¿Estás seguro que deseas eliminar este producto?")
                    .setNegativeButton("Cancelar", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setPositiveButton("Eliminar", (dialog, which) -> {

                        productoRepositorio.eliminarProducto(idProducto, new ProductoContrato.OperacionCallback() {
                            @Override
                            public void onExito(String mensaje) {
                                Toast.makeText(DetalleProductoActivity.this, mensaje, Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(DetalleProductoActivity.this, error, Toast.LENGTH_SHORT).show();                            }
                        });
                    })
                    .show();
        });
    }
}