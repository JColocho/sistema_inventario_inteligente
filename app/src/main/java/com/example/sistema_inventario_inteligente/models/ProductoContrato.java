package com.example.sistema_inventario_inteligente.models;

import java.util.List;

public interface ProductoContrato {
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
    void insertarProducto(Producto producto, OperacionCallback callback);
    void actualizarProducto(Producto producto, OperacionCallback callback);
    void eliminarProducto(String idProducto, OperacionCallback callback);
    void obtenerProductoId(String idProducto, LeerIdCallback callback);
    void obtenerProductosEnTiempoReal(LeerCallback callback);
}
