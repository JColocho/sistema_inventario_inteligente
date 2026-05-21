package com.example.sistema_inventario_inteligente.location;

import android.content.Context;
import android.location.Location;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class LocationHelper {
    /*
    private final FusedLocationProviderClient fusedClient;

    public LocationHelper(Context context) {
        fusedClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @SuppressWarnings("MissingPermission")
    public void obtenerUbicacionActual(OnSuccessListener<Location> onSuccess,
                                       OnFailureListener onFailure) {
        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        onSuccess.onSuccess(location);
                    } else {
                        onFailure.onFailure(new Exception("Ubicación no disponible. Activa el GPS."));
                    }
                })
                .addOnFailureListener(onFailure);
    }

     */
}
