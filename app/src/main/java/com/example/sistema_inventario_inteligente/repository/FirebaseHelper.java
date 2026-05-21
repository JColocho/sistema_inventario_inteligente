package com.example.sistema_inventario_inteligente.repository;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseHelper {
    private static FirebaseHelper instance;
    private final FirebaseDatabase database;

    private FirebaseHelper() {
        database = FirebaseDatabase.getInstance();
        // Debe llamarse una sola vez antes de cualquier operación de Firebase.
        database.setPersistenceEnabled(true);
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) instance = new FirebaseHelper();
        return instance;
    }

    public DatabaseReference productos() {
        return database.getReference("productos");
    }

    public DatabaseReference sucursales() {
        return database.getReference("sucursales");
    }
}
