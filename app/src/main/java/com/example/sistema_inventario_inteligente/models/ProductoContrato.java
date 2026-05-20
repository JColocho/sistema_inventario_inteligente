package com.example.sistema_inventario_inteligente.models;

import android.net.Uri;

import java.util.List;

public interface ProductoContrato {

    //Metodos para la gestión de productos en firebase
    void insertarProducto(Producto producto, OperacionCallback callback);
    void actualizarProducto(Producto producto, OperacionCallback callback);
    void eliminarProducto(String idProducto, String urlImagen, String urlModelo3D, OperacionCallback callback);
    void obtenerProductoId(String idProducto, LeerIdCallback callback);
    void obtenerProductosEnTiempoReal(String categoria, LeerCallback callback);

    //Metodos para gestión de imagenes de referencia y modelos 3D en el storage
    void subirImagenStorage(Uri uriImagen, StorageCallBack callBack);
    void actualizarImagenStorage(String urlImagen, Uri nuevaImagen, StorageCallBack callback);
    void eliminarImagenStorage(String uriImagen, OperacionCallback callBack);
    void subirModelo3DStorage(Uri uriModelo, StorageCallBack callBack);
    void actualizarModelo3DStorage(String urlModelo, Uri nuevoModelo, StorageCallBack callback);
    void eliminarModelo3DStorage(String urlModelo, OperacionCallback callBack);
    interface OperacionCallback {
        void onExito(String mensaje);
        void onError(String error);
    }

    interface LeerCallback {
        void onProductosCargados(List<Producto> productos);
        void onError(String error);
    }
    interface LeerIdCallback {
        void onProductoCargado(Producto productoObtenido);
        void onError(String error);
    }
    interface StorageCallBack {
        void onExito(String urlDescarga);
        void onError(String error);
    }
}
