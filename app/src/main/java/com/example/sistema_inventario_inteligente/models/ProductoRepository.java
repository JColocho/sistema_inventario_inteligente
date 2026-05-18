package com.example.sistema_inventario_inteligente.models;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ProductoRepository implements ProductoContrato{
    private DatabaseReference productosRef;
    private ValueEventListener listenerTiempoReal;

    public ProductoRepository() {
        // Inicializamos la referencia al nodo "Productos" una sola vez
        productosRef = FirebaseDatabase.getInstance().getReference("Productos");
    }

    @Override
    public void insertarProducto(Producto producto, OperacionCallback callback) {
        //Generamos un ID único aleatorio en Firebase
        String idGenerado = productosRef.push().getKey();

        if (idGenerado != null) {
            //Guardamos ese ID dentro de tu objeto Producto
            producto.setIdProducto(idGenerado);

            //Insertamos el objeto en la base de datos bajo ese ID
            productosRef.child(idGenerado).setValue(producto)
                    .addOnSuccessListener(aVoid -> callback.onExito("Producto agregado con éxito"))
                    .addOnFailureListener(e -> callback.onError("Error al insertar: " + e.getMessage()));
        } else {
            callback.onError("No se pudo generar el ID del producto.");
        }
    }

    @Override
    public void actualizarProducto(Producto producto, OperacionCallback callback) {

        //Validando que el producto tenga ID
        if (producto.getIdProducto() == null || producto.getIdProducto().isEmpty()) {
            callback.onError("El producto no tiene un ID válido para actualizar.");
            return;
        }

        // Apuntamos al ID específico y sobrescribimos sus datos
        productosRef.child(producto.getIdProducto()).setValue(producto)
                .addOnSuccessListener(aVoid -> callback.onExito("Producto actualizado correctamente"))
                .addOnFailureListener(e -> callback.onError("Error al actualizar: " + e.getMessage()));
    }

    @Override
    public void eliminarProducto(String idProducto, OperacionCallback callback) {
        DatabaseReference productoEliminar = FirebaseDatabase.getInstance().getReference("Productos");
        // Apuntamos al ID y lo eliminamos
        productoEliminar.child(idProducto).removeValue()
                .addOnSuccessListener(aVoid -> callback.onExito("Producto eliminado correctamente"))
                .addOnFailureListener(e -> callback.onError("Error al eliminar: " + e.getMessage()));
    }

    @Override
    public void obtenerProductosEnTiempoReal(LeerCallback callback) {
        // Inicializamos el escuchador
        listenerTiempoReal = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Producto> listaProductos = new ArrayList<>();

                // Recorremos todos los nodos hijos en "Productos"
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Producto producto = ds.getValue(Producto.class);
                    listaProductos.add(producto);
                }

                // Enviamos la lista llena a la Activity
                callback.onProductosCargados(listaProductos);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Error al leer datos: " + error.getMessage());
            }
        };

        // Conectamos el escuchador a Firebase
        productosRef.addValueEventListener(listenerTiempoReal);
    }

    @Override
    public void obtenerProductoId(String idProducto, LeerIdCallback callback) {
        productosRef = productosRef.child(idProducto);

        productosRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    Producto producto = snapshot.getValue(Producto.class);
                    callback.onProductoCargado(producto);
                }
                else {
                    callback.onError("No se encontró el producto");
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Error al bucar el producto: " + error.getMessage());
            }
        });
    }
}
