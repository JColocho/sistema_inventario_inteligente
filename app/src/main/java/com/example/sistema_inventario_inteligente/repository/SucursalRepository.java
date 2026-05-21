package com.example.sistema_inventario_inteligente.repository;

import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public interface SucursalRepository {
    void insertar(Sucursal s, OnSuccessListener<String> onSuccess, OnFailureListener onFailure);

    void actualizar(Sucursal s, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure);

    void eliminar(String sucursalId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure);

    void obtenerPorId(String sucursalId, OnSuccessListener<Sucursal> onSuccess, OnFailureListener onFailure);

    void obtenerTodas(OnSuccessListener<List<Sucursal>> onSuccess, OnFailureListener onFailure);

    void obtenerMasCercana(double latitud, double longitud,
                           OnSuccessListener<Sucursal> onSuccess,
                           OnFailureListener onFailure);

    /**
     * Devuelve la sucursal más cercana cuya distancia al usuario sea <= radioMetros.
     * Llama a onSuccess con null si el usuario no está dentro del radio de ninguna sucursal.
     */
    void obtenerEnRadio(double latitud, double longitud, float radioMetros,
                        OnSuccessListener<Sucursal> onSuccess,
                        OnFailureListener onFailure);
}
