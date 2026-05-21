package com.example.sistema_inventario_inteligente.models;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SucursalRepository implements SucursalContrato {

    @Override
    public void obtenerTodas(LeerCallback callback) {
        FirebaseDatabase.getInstance().getReference("sucursales")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Sucursal> lista = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Sucursal s = child.getValue(Sucursal.class);
                            if (s != null) {
                                s.setIdSucursal(child.getKey());
                                lista.add(s);
                            }
                        }
                        callback.onSucursalesCargadas(lista);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Error al cargar sucursales: " + error.getMessage());
                    }
                });
    }
}
