package com.example.sistema_inventario_inteligente.models;

import java.util.List;

public interface SucursalContrato {
    void obtenerTodas(LeerCallback callback);

    interface LeerCallback {
        void onSucursalesCargadas(List<Sucursal> sucursales);
        void onError(String error);
    }
}
