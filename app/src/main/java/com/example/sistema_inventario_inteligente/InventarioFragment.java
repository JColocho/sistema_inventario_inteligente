package com.example.sistema_inventario_inteligente;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.sistema_inventario_inteligente.adapters.ProductoAdapater;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class InventarioFragment extends Fragment {
    public RecyclerView rvProducto;
    public Button btnAgregar;
    public ProductoAdapater productoAdapater;
    public ArrayList<Producto> listaProductos;


    public InventarioFragment() {
        // Required empty public constructor
    }
    public static InventarioFragment newInstance() {
        InventarioFragment fragment = new InventarioFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventario, container, false);

        listaProductos = new ArrayList<>();
        productoAdapater = new ProductoAdapater(listaProductos);

        rvProducto = view.findViewById(R.id.rvProductos);
        btnAgregar = view.findViewById(R.id.btnAgregar);

        rvProducto.setLayoutManager(new LinearLayoutManager(getContext()));

        btnAgregar.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), AgregarProductoActivity.class);
            startActivity(intent);
        });

        rvProducto.setAdapter(productoAdapater);
        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        cargarProductos();
    }
    private void cargarProductos(){
        DatabaseReference productosRef = FirebaseDatabase.getInstance().getReference("Productos");

        productosRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaProductos.clear();
                for (DataSnapshot dataSnapshot: snapshot.getChildren()){
                    Producto producto = dataSnapshot.getValue(Producto.class);
                    listaProductos.add(producto);
                }
                productoAdapater.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
}