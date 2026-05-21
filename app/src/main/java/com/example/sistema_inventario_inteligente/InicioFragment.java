package com.example.sistema_inventario_inteligente;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.adapters.ProductoAdapter;
import com.example.sistema_inventario_inteligente.ar.EscanearActivity;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class InicioFragment extends Fragment {

    private MaterialCardView cardEscanear, cardAgregar;
    private RecyclerView rvRecientes;
    private View progressRecientes, layoutVacioRecientes;

    private ProductoAdapter adapterRecientes;
    private final ArrayList<Producto> listaRecientes = new ArrayList<>();
    private final ProductoContrato repositorio = new ProductoRepository();

    public InicioFragment() {}

    public static InicioFragment newInstance() {
        InicioFragment fragment = new InicioFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inicio, container, false);

        cardEscanear         = view.findViewById(R.id.cardEscanear);
        cardAgregar          = view.findViewById(R.id.cardAgregar);
        rvRecientes          = view.findViewById(R.id.rvRecientes);
        progressRecientes    = view.findViewById(R.id.progressRecientes);
        layoutVacioRecientes = view.findViewById(R.id.layoutVacioRecientes);

        adapterRecientes = new ProductoAdapter(listaRecientes);
        rvRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecientes.setAdapter(adapterRecientes);
        rvRecientes.setNestedScrollingEnabled(false);

        cardEscanear.setOnClickListener(v ->
                startActivity(new Intent(getContext(), EscanearActivity.class)));

        cardAgregar.setOnClickListener(v ->
                startActivity(new Intent(getContext(), AgregarProductoActivity.class)));

        cargarRecientes();
        return view;
    }

    private void cargarRecientes() {
        repositorio.obtenerProductosEnTiempoReal("", new ProductoContrato.LeerCallback() {
            @Override
            public void onProductosCargados(List<Producto> productos) {
                if (getContext() == null) return;

                progressRecientes.setVisibility(View.GONE);
                listaRecientes.clear();

                // Mostrar los últimos 3 (Firebase devuelve en orden de inserción)
                int inicio = Math.max(0, productos.size() - 3);
                listaRecientes.addAll(productos.subList(inicio, productos.size()));

                if (listaRecientes.isEmpty()) {
                    rvRecientes.setVisibility(View.GONE);
                    layoutVacioRecientes.setVisibility(View.VISIBLE);
                } else {
                    layoutVacioRecientes.setVisibility(View.GONE);
                    rvRecientes.setVisibility(View.VISIBLE);
                    adapterRecientes.notifyDataSetChanged();
                }
            }

            @Override
            public void onError(String error) {
                if (getContext() != null)
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                progressRecientes.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        repositorio.detenerEscucha();
    }
}
