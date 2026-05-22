package com.example.sistema_inventario_inteligente;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.sistema_inventario_inteligente.glide.GlideApp;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import io.github.sceneview.SceneView;
import io.github.sceneview.node.ModelNode;

public class DetalleProductoActivity extends AppCompatActivity {

    private String idProducto = "", urlImagenRef = "", urlModelo3D = "";
    public TextView txtNombre, txtDescripcion, txtSucursal, txtCategoria, txtPrecio, txtCantidad;
    public ImageView imgProducto, btnCerrar, btnEliminar, btnEditar;
    private ProductoContrato productoRepositorio;
    private SceneView sceneViewDetalle;
    private ModelNode modelNodeDetalle;
    private TextView tabImagen, tabModelo3D, tvCargandoModelo;
    private LinearLayout layoutTabsVisor;
    private boolean modeloCargado = false;
    private boolean tabImagenActivo = true;
    private boolean vuelveDeEdicion = false;

    // Detecta el regreso de EditarProductoActivity sin necesidad de onResume
    private final ActivityResultLauncher<Intent> editarLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> vuelveDeEdicion = true);

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
        txtSucursal = findViewById(R.id.txtSucursalDetalle);
        txtCategoria = findViewById(R.id.txtCategoriaDetalle);
        txtPrecio = findViewById(R.id.txtPrecioDetalle);
        txtCantidad = findViewById(R.id.txtCantidadDetalle);
        btnCerrar = findViewById(R.id.btnDetalleBack);
        btnEliminar = findViewById(R.id.btnElminarProducto);
        btnEditar = findViewById(R.id.btnEditarView);
        sceneViewDetalle = findViewById(R.id.sceneViewDetalle);
        tvCargandoModelo = findViewById(R.id.tvCargandoModelo);
        tabImagen = findViewById(R.id.tabImagen);
        tabModelo3D = findViewById(R.id.tabModelo3D);
        layoutTabsVisor = findViewById(R.id.layoutTabsVisor);

        idProducto = getIntent().getStringExtra("idProducto");

        tabImagen.setOnClickListener(v -> {
            tabImagenActivo = true;
            mostrarImagen();
        });
        tabModelo3D.setOnClickListener(v -> {
            tabImagenActivo = false;
            mostrarModelo3D();
        });

        btnCerrar.setOnClickListener(v -> finish());

        btnEliminar.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Eliminar producto")
                        .setMessage("¿Estás seguro que deseas eliminar este producto?")
                        .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("Eliminar", (dialog, which) ->
                                productoRepositorio.eliminarProducto(
                                        idProducto, urlImagenRef, urlModelo3D,
                                        new ProductoContrato.OperacionCallback() {
                                            @Override public void onExito(String mensaje) {
                                                Toast.makeText(DetalleProductoActivity.this,
                                                        mensaje, Toast.LENGTH_SHORT).show();
                                                finish();
                                            }
                                            @Override public void onError(String error) {
                                                Toast.makeText(DetalleProductoActivity.this,
                                                        error, Toast.LENGTH_SHORT).show();
                                            }
                                        }))
                        .show());

        // Usa el launcher para detectar el regreso de Editar
        btnEditar.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditarProductoActivity.class);
            intent.putExtra("idProducto", idProducto);
            editarLauncher.launch(intent);
        });

        cargarDatosProducto();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Validar que el usuario regresa de editar
        if (vuelveDeEdicion) {
            vuelveDeEdicion = false;
            resetearModelo3D();     // limpia el modelo antiguo antes de recargar
            cargarDatosProducto();  // trae los datos actualizados desde Firebase
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sceneViewDetalle != null) sceneViewDetalle.destroy();
    }

    //Metodo para cargar los datos del producto
    private void cargarDatosProducto() {
        productoRepositorio.obtenerProductoId(idProducto, new ProductoContrato.LeerIdCallback() {
            @Override
            public void onProductoCargado(Producto producto) {
                if (producto == null) { finish(); return; }

                urlImagenRef = producto.getUrlImagenProducto();
                urlModelo3D  = producto.getUrlModelo3D() != null ? producto.getUrlModelo3D() : "";

                // Nombre de sucursal — listener de única lectura para evitar memory leaks
                DatabaseReference sucursalRef = FirebaseDatabase.getInstance()
                        .getReference("sucursales");
                sucursalRef.child(producto.getIdSucursal())
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                Sucursal s = snapshot.getValue(Sucursal.class);
                                if (s != null) txtSucursal.setText(s.getNombre());
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) { }
                        });

                txtNombre.setText(producto.getNombre());
                txtDescripcion.setText(producto.getDescripcion());
                txtCategoria.setText(producto.getCategoria());
                txtPrecio.setText(String.format("$%.2f", producto.getPrecio()));
                txtCantidad.setText(producto.getCantidad().toString());

                StorageReference imageRef = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(urlImagenRef);
                GlideApp.with(DetalleProductoActivity.this)
                        .load(imageRef)
                        .placeholder(R.drawable.camara)
                        .error(R.drawable.camara)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .centerCrop()
                        .into(imgProducto);

                // Visibilidad del tab 3D según si el producto tiene modelo
                tabModelo3D.setVisibility(urlModelo3D.isEmpty() ? View.GONE : View.VISIBLE);

                // Si el usuario estaba viendo el modelo 3D cuando se recargaron los datos,
                // volver a disparar la carga con la URL actualizada (puede haber cambiado)
                if (!tabImagenActivo && !urlModelo3D.isEmpty()) {
                    mostrarModelo3D();
                } else {
                    mostrarImagen();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DetalleProductoActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    //Metodo para resetear el modelo 3D
    private void resetearModelo3D() {
        if (modelNodeDetalle != null) {
            //Remueve el modelo de la vista
            sceneViewDetalle.removeChildNode(modelNodeDetalle);
            modelNodeDetalle = null;
        }
        modeloCargado = false;
        tvCargandoModelo.setText("Cargando modelo…");  // restaura texto original
    }


    //Metodo para cargar el modelo 3D
    private void cargarModelo3D(String url) {
        sceneViewDetalle.getModelLoader().loadModelInstanceAsync(
                url,
                name -> null,
                modelInstance -> {
                    if (modelInstance != null) {
                        modelNodeDetalle = new io.github.sceneview.node.ModelNode(
                                modelInstance, true, 0.5f,
                                new dev.romainguy.kotlin.math.Float3(0f, 0f, 0f));
                        runOnUiThread(() -> {
                            sceneViewDetalle.addChildNode(modelNodeDetalle);
                            tvCargandoModelo.setVisibility(View.GONE);
                        });
                    } else {
                        runOnUiThread(() ->
                                tvCargandoModelo.setText("No se pudo cargar el modelo"));
                    }
                    return kotlin.Unit.INSTANCE;
                });
    }

    //Cambio entre vistas
    private void mostrarImagen() {
        imgProducto.setVisibility(View.VISIBLE);
        sceneViewDetalle.setVisibility(View.GONE);
        tvCargandoModelo.setVisibility(View.GONE);
        tabImagen.setBackgroundResource(R.drawable.bg_button);
        tabImagen.setTextColor(ContextCompat.getColor(this, R.color.bg_primary));
        tabModelo3D.setBackgroundResource(R.drawable.bg_field);
        tabModelo3D.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    private void mostrarModelo3D() {
        imgProducto.setVisibility(View.GONE);
        sceneViewDetalle.setVisibility(View.VISIBLE);
        tabModelo3D.setBackgroundResource(R.drawable.bg_button);
        tabModelo3D.setTextColor(ContextCompat.getColor(this, R.color.bg_primary));
        tabImagen.setBackgroundResource(R.drawable.bg_field);
        tabImagen.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        // Carga el modelo solo si no estaba cargado
        if (!modeloCargado) {
            modeloCargado = true;
            tvCargandoModelo.setVisibility(View.VISIBLE);
            cargarModelo3D(urlModelo3D);
        }
    }
}