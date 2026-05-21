package com.example.sistema_inventario_inteligente;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.adapters.ProductoAdapter;
import com.example.sistema_inventario_inteligente.ar.EscanearActivity;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.example.sistema_inventario_inteligente.models.SucursalContrato;
import com.example.sistema_inventario_inteligente.models.SucursalRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class InicioFragment extends Fragment {

    private MaterialCardView cardEscanear, cardAgregar;
    private RecyclerView rvRecientes;
    private View progressRecientes, layoutVacioRecientes;
    private TextView tvSucursalCercana, tvDistanciaGps;

    private ProductoAdapter adapterRecientes;
    private final ArrayList<Producto> listaRecientes = new ArrayList<>();
    private final ProductoContrato repositorio = new ProductoRepository();
    private final SucursalContrato repositorioSucursal = new SucursalRepository();
    private FusedLocationProviderClient fusedClient;

    private final ActivityResultLauncher<String> permisoUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) obtenerUbicacion();
                else if (tvSucursalCercana != null) tvSucursalCercana.setText("Sin permiso GPS");
            });

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
        tvSucursalCercana    = view.findViewById(R.id.tvSucursalCercana);
        tvDistanciaGps       = view.findViewById(R.id.tvDistanciaGps);

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());

        adapterRecientes = new ProductoAdapter(listaRecientes);
        rvRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecientes.setAdapter(adapterRecientes);
        rvRecientes.setNestedScrollingEnabled(false);

        cardEscanear.setOnClickListener(v ->
                startActivity(new Intent(getContext(), EscanearActivity.class)));

        cardAgregar.setOnClickListener(v ->
                startActivity(new Intent(getContext(), AgregarProductoActivity.class)));

        verificarPermiso();
        cargarRecientes();
        return view;
    }

    private void verificarPermiso() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacion();
        } else {
            permisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void obtenerUbicacion() {
        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || getContext() == null) return;
            buscarSucursalMasCercana(location);
        });
    }

    private void buscarSucursalMasCercana(Location location) {
        repositorioSucursal.obtenerTodas(new SucursalContrato.LeerCallback() {
            @Override
            public void onSucursalesCargadas(List<Sucursal> sucursales) {
                if (getContext() == null || sucursales.isEmpty()) return;
                Sucursal cercana = null;
                float minDist = Float.MAX_VALUE;
                float[] res = new float[1];
                for (Sucursal s : sucursales) {
                    Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                            s.getLatitud(), s.getLongitud(), res);
                    if (res[0] < minDist) { minDist = res[0]; cercana = s; }
                }
                if (cercana == null) return;
                tvSucursalCercana.setText(cercana.getNombre());
                String dist = minDist >= 1000
                        ? String.format("%.1f km", minDist / 1000f)
                        : Math.round(minDist) + " m";
                tvDistanciaGps.setText(dist);
                tvDistanciaGps.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String error) {
                if (getContext() != null)
                    tvSucursalCercana.setText("No disponible");
            }
        });
    }

    private void cargarRecientes() {
        repositorio.obtenerProductosEnTiempoReal("", new ProductoContrato.LeerCallback() {
            @Override
            public void onProductosCargados(List<Producto> productos) {
                if (getContext() == null) return;
                progressRecientes.setVisibility(View.GONE);
                listaRecientes.clear();
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
