package com.example.sistema_inventario_inteligente.models;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.example.sistema_inventario_inteligente.EditarProductoActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

public class ProductoRepository implements ProductoContrato{
    private ValueEventListener listenerTiempoReal;

    public ProductoRepository() {
    }

    //Metodo para insertar productos
    @Override
    public void insertarProducto(Producto producto, OperacionCallback callback) {
        DatabaseReference productosRef = FirebaseDatabase.getInstance().getReference("Productos");
        //Generamos un ID único aleatorio en Firebase
        String idGenerado = productosRef.push().getKey();

        //Validando que el ID del productos se haya generado
        if (idGenerado != null) {

            //Capturamos el ID del producto
            producto.setIdProducto(idGenerado);

            //Insertamos el objeto en la base de datos bajo ese ID
            productosRef.child(idGenerado).setValue(producto)
                    .addOnSuccessListener(aVoid -> callback.onExito("Producto agregado con éxito"))
                    .addOnFailureListener(e -> callback.onError("Error al insertar: " + e.getMessage()));
        } else {
            callback.onError("No se pudo generar el ID del producto.");
        }
    }

    //Metodo para actualizar los datos de un producto
    @Override
    public void actualizarProducto(Producto producto, OperacionCallback callback) {
        DatabaseReference actualizarRef = FirebaseDatabase.getInstance().getReference("Productos");

        //Hacemos referencia al producto mediante el ID para actualizar los datos
        actualizarRef.child(producto.getIdProducto()).setValue(producto)
                .addOnSuccessListener(aVoid -> {
                    callback.onExito("Producto actualizado correctamente");
                })
                .addOnFailureListener(e -> callback.onError("Error al actualizar: " + e.getMessage()));
    }

    //Metodo para eliminar un producto
    @Override
    public void eliminarProducto(String idProducto, String urlImagen, String urlModelo3D, OperacionCallback callback) {

        eliminarImagenStorage(urlImagen, new OperacionCallback() {
            @Override
            public void onExito(String mensaje) {
                eliminarModelo3DStorage(urlModelo3D, new OperacionCallback() {
                    @Override
                    public void onExito(String mensaje) {
                        DatabaseReference productoEliminar = FirebaseDatabase.getInstance().getReference("Productos");
                        //Referenciamos mediante el ID el producto a eliminar.
                        productoEliminar.child(idProducto).removeValue()
                                .addOnSuccessListener(aVoid -> callback.onExito("Producto eliminado correctamente"))
                                .addOnFailureListener(e -> callback.onError("Error al eliminar: " + e.getMessage()));
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void obtenerProductosEnTiempoReal(String categoria, LeerCallback callback) {
        DatabaseReference productosRef = FirebaseDatabase.getInstance().getReference("Productos");
        //Inicializamos el escuchador
        listenerTiempoReal = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Producto> listaProductos = new ArrayList<>();

                // Recorremos todos los nodos hijos en "Productos"
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Producto producto = ds.getValue(Producto.class);
                    listaProductos.add(producto);
                }

                //Enviando todos los datos obtenidos
                callback.onProductosCargados(listaProductos);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Error al leer datos: " + error.getMessage());
            }
        };

        if (categoria.isEmpty()){
            // Conectamos el escuchador a Firebase
            productosRef.addValueEventListener(listenerTiempoReal);
        }
        else {
            //Conectamos el escuchador a Firebase y filtramos los datos según categoria
            productosRef.orderByChild("categoria").equalTo(categoria).addValueEventListener(listenerTiempoReal);
        }
    }

    @Override
    public void detenerEscucha() {
        if (listenerTiempoReal != null) {
            FirebaseDatabase.getInstance().getReference("Productos")
                    .removeEventListener(listenerTiempoReal);
            listenerTiempoReal = null;
        }
    }

    //Metodo para obtener los datos de un producto
    @Override
    public void obtenerProductoId(String idProducto, LeerIdCallback callback) {

        DatabaseReference productosRef = FirebaseDatabase.getInstance().getReference("Productos");

        //Referenciamos el ID del producto
        productosRef = productosRef.child(idProducto);

        //Iniciando el escuchador
        productosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                //Validamos que se haya encontrado el producto
                if (snapshot.exists()){
                    //Capturamos los datos del producto
                    Producto producto = snapshot.getValue(Producto.class);
                    //Enviando los datos del producto
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

    //Metodo para subir la imagen al storage y asignar el url de la imagen del producto
    @Override
    public void subirImagenStorage(Uri uriImagen, StorageCallBack callBack) {
        //Referenciamos el Storages donde se almacenara la imagen
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("imagenes_productos/" + System.currentTimeMillis() + ".jpg");

        //Subiendo la imagen al storage y obtenemos la url de la imagen
        storageRef.putFile(uriImagen).continueWithTask(task -> storageRef.getDownloadUrl()).addOnSuccessListener(uri -> {
            callBack.onExito(uri.toString());
        }).addOnFailureListener(e -> {
            callBack.onError("Error al subir la imagen: " + e.getMessage());
        });
    }

    @Override
    public void actualizarImagenStorage(String urlImagen, Uri nuevaImagen, StorageCallBack callback) {

        StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(urlImagen);

        imageRef.putFile(nuevaImagen)
                .continueWithTask(task -> imageRef.getDownloadUrl())
                .addOnSuccessListener(uri -> {
                    callback.onExito(uri.toString());
                }).addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    @Override
    public void eliminarImagenStorage(String uriImagen, OperacionCallback callBack) {
        StorageReference imagenRef = FirebaseStorage.getInstance().getReferenceFromUrl(uriImagen);
        imagenRef.delete().addOnSuccessListener(unused -> {
            callBack.onExito("Imagen Eliminada con exito.");
        }).addOnFailureListener(e -> {
            callBack.onError("Error al eliminar la imagen: " + e.getMessage());
        });
    }

    //Metodo para subir la imagen al storage y asignar el url del modelo 3D del producto
    @Override
    public void subirModelo3DStorage(Uri uriModelo, StorageCallBack callBack) {
        //Referenciamos el Storages donde se almacenara el modelo 3D
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("modelo_3d/" + System.currentTimeMillis() + "glb");

        //Subiendo el modelo 3D al storage y obtenemos la url del modelo 3D
        storageRef.putFile(uriModelo).continueWithTask(task -> storageRef.getDownloadUrl()).addOnSuccessListener(uri -> {
            callBack.onExito(uri.toString());
        }).addOnFailureListener(e -> {
            callBack.onError("Error al subir el modelo 3D: " + e.getMessage());
        });
    }

    @Override
    public void actualizarModelo3DStorage(String urlModelo, Uri nuevoModelo, StorageCallBack callback) {
        StorageReference modeloRef = FirebaseStorage.getInstance().getReferenceFromUrl(urlModelo);

        modeloRef.putFile(nuevoModelo).continueWithTask(task -> modeloRef.getDownloadUrl())
                .addOnSuccessListener(uri -> {
                    callback.onExito(uri.toString());
                })
                .addOnFailureListener(e -> {
                    callback.onError("Error al actualizar el modelo: " + e.getMessage());
                });
    }

    @Override
    public void eliminarModelo3DStorage(String urlModelo, OperacionCallback callBack) {
        StorageReference  modeloRef = FirebaseStorage.getInstance().getReferenceFromUrl(urlModelo);
        modeloRef.delete().addOnSuccessListener(unused -> {
            callBack.onExito("Modelo elimnado con exitoso.");
        }).addOnFailureListener(e -> {
            callBack.onError("Error al eliminar el modelo 3D" + e.getMessage());
        });
    }
}