package com.example.sistema_inventario_inteligente.repository;

import android.location.Location;

import androidx.annotation.NonNull;

import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SucursalRepositoryImpl implements SucursalRepository{
    public static final float RADIO_SUCURSAL_METROS = 100f;

    private final DatabaseReference ref;
    public SucursalRepositoryImpl() {
        ref = FirebaseHelper.getInstance().sucursales();
    }

    @Override
    public void insertar(Sucursal s, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {

    }

    @Override
    public void actualizar(Sucursal s, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {

    }

    @Override
    public void eliminar(String sucursalId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {

    }

    @Override
    public void obtenerPorId(String sucursalId, OnSuccessListener<Sucursal> onSuccess, OnFailureListener onFailure) {
        ref.child(sucursalId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Sucursal s = snapshot.getValue(Sucursal.class);
                        if (s != null) {
                            s.setIdSucursal(snapshot.getKey());
                            onSuccess.onSuccess(s);
                        } else {
                            onFailure.onFailure(new Exception("Sucursal no encontrada: " + sucursalId));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        onFailure.onFailure(error.toException());
                    }
                });
    }

    @Override
    public void obtenerTodas(OnSuccessListener<List<Sucursal>> onSuccess, OnFailureListener onFailure) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Sucursal> sucursales = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Sucursal s = child.getValue(Sucursal.class);
                    if (s != null) {
                        s.setIdSucursal(child.getKey());
                        sucursales.add(s);
                    }
                }
                onSuccess.onSuccess(sucursales);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onFailure.onFailure(error.toException());
            }
        });
    }

    @Override
    public void obtenerMasCercana(double latitud, double longitud, OnSuccessListener<Sucursal> onSuccess, OnFailureListener onFailure) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Sucursal masCercana = null;
                float distanciaMin = Float.MAX_VALUE;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Sucursal s = child.getValue(Sucursal.class);
                    if (s == null) continue;
                    s.setIdSucursal(child.getKey());

                    float[] resultado = new float[1];
                    Location.distanceBetween(latitud, longitud, s.getLatitud(), s.getLongitud(), resultado);

                    if (resultado[0] < distanciaMin) {
                        distanciaMin = resultado[0];
                        masCercana = s;
                    }
                }

                if (masCercana != null) {
                    onSuccess.onSuccess(masCercana);
                } else {
                    onFailure.onFailure(new Exception("No hay sucursales registradas"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onFailure.onFailure(error.toException());
            }
        });
    }

    @Override
    public void obtenerEnRadio(double latitud, double longitud, float radioMetros, OnSuccessListener<Sucursal> onSuccess, OnFailureListener onFailure) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Sucursal enRango = null;
                float distanciaMin = Float.MAX_VALUE;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Sucursal s = child.getValue(Sucursal.class);
                    if (s == null) continue;
                    s.setIdSucursal(child.getKey());
                    float[] res = new float[1];
                    Location.distanceBetween(latitud, longitud, s.getLatitud(), s.getLongitud(), res);
                    if (res[0] <= radioMetros && res[0] < distanciaMin) {
                        distanciaMin = res[0];
                        enRango = s;
                    }
                }
                onSuccess.onSuccess(enRango); // null = fuera de todas las sucursales
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onFailure.onFailure(error.toException());
            }
        });
    }
}
