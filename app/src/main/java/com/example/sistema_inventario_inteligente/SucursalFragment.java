package com.example.sistema_inventario_inteligente;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.adapters.SucursalAdapter;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SucursalFragment extends Fragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    private RecyclerView rvSucursales;
    private ProgressBar progressSucursales;
    private TextView tvSucursalCercana;

    private SucursalAdapter adapter;
    private final List<Sucursal> listaSucursales = new ArrayList<>();

    private FusedLocationProviderClient fusedClient;
    private double latUser = 0, lngUser = 0;
    private String idSucursalCercana = null;
    // Evita pedir permiso/GPS de nuevo al volver de los propios diálogos de permiso/GPS
    private boolean permisoGestionado = false;

    private DatabaseReference sucursalesRef;
    private ValueEventListener sucursalesListener;

    private final ActivityResultLauncher<IntentSenderRequest> gpsSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    obtenerUbicacion();
                }
            });

    private final ActivityResultLauncher<String> permisoUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) verificarGpsActivo();
                else if (tvSucursalCercana != null) tvSucursalCercana.setText("Sin permiso GPS");
            });

    public SucursalFragment() {}

    public static SucursalFragment newInstance() {
        SucursalFragment fragment = new SucursalFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sucursal, container, false);

        mapView            = view.findViewById(R.id.mapView);
        rvSucursales       = view.findViewById(R.id.rvSucursales);
        progressSucursales = view.findViewById(R.id.progressSucursales);
        tvSucursalCercana  = view.findViewById(R.id.tvSucursalCercana);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        adapter = new SucursalAdapter();
        rvSucursales.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSucursales.setAdapter(adapter);
        rvSucursales.setNestedScrollingEnabled(false);

        cargarSucursales();
        return view;
    }

    // Se llama cada vez que el fragment vuelve a ser visible
    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (!permisoGestionado) {
            permisoGestionado = true;
            pedirPermiso();
        }
    }

    // Al salir del fragment (navegar a otra tab) se resetea para pedirlo de nuevo al volver
    @Override
    public void onStop() {
        super.onStop();
        permisoGestionado = false;
    }

    private void pedirPermiso() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            verificarGpsActivo();
        } else {
            permisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void verificarGpsActivo() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                .setMaxUpdates(1).build();
        LocationSettingsRequest settingsReq = new LocationSettingsRequest.Builder()
                .addLocationRequest(req).build();
        LocationServices.getSettingsClient(requireContext())
                .checkLocationSettings(settingsReq)
                .addOnSuccessListener(r -> obtenerUbicacion())
                .addOnFailureListener(e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            IntentSenderRequest ir = new IntentSenderRequest.Builder(
                                    ((ResolvableApiException) e).getResolution()).build();
                            gpsSettingsLauncher.launch(ir);
                        } catch (Exception ignored) {}
                    }
                });
    }

    @SuppressWarnings("MissingPermission")
    private void obtenerUbicacion() {
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null || getContext() == null) return;
                    latUser = location.getLatitude();
                    lngUser = location.getLongitude();
                    actualizarConUbicacion();
                });
    }

    private void cargarSucursales() {
        sucursalesRef = FirebaseDatabase.getInstance().getReference("sucursales");
        sucursalesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getContext() == null) return;
                listaSucursales.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Sucursal s = child.getValue(Sucursal.class);
                    if (s != null) {
                        s.setIdSucursal(child.getKey());
                        listaSucursales.add(s);
                    }
                }
                progressSucursales.setVisibility(View.GONE);
                rvSucursales.setVisibility(View.VISIBLE);
                actualizarConUbicacion();
                dibujarMarcadores();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() == null) return;
                progressSucursales.setVisibility(View.GONE);
            }
        };
        sucursalesRef.addValueEventListener(sucursalesListener);
    }

    private void actualizarConUbicacion() {
        if (listaSucursales.isEmpty()) return;

        if (latUser != 0 && lngUser != 0) {
            Sucursal cercana = null;
            float minDist = Float.MAX_VALUE;
            float[] resultado = new float[1];
            for (Sucursal s : listaSucursales) {
                Location.distanceBetween(latUser, lngUser, s.getLatitud(), s.getLongitud(), resultado);
                if (resultado[0] < minDist) {
                    minDist = resultado[0];
                    cercana = s;
                }
            }
            if (cercana != null) {
                idSucursalCercana = cercana.getIdSucursal();
                tvSucursalCercana.setText(cercana.getNombre());
                adapter.marcarSucursalActiva(idSucursalCercana);
                dibujarMarcadores();
            }
            adapter.actualizarLista(listaSucursales, latUser, lngUser);
        } else {
            adapter.actualizarLista(listaSucursales);
        }
    }

    private void dibujarMarcadores() {
        if (googleMap == null || listaSucursales.isEmpty()) return;
        googleMap.clear();

        LatLng centro = null;
        for (Sucursal s : listaSucursales) {
            LatLng pos = new LatLng(s.getLatitud(), s.getLongitud());
            boolean esActiva = s.getIdSucursal() != null && s.getIdSucursal().equals(idSucursalCercana);
            float hue = esActiva ? BitmapDescriptorFactory.HUE_CYAN : BitmapDescriptorFactory.HUE_VIOLET;
            googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(s.getNombre())
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
            if (esActiva) centro = pos;
        }

        if (centro != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(centro, 13f));
        } else if (!listaSucursales.isEmpty()) {
            Sucursal primera = listaSucursales.get(0);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(primera.getLatitud(), primera.getLongitud()), 12f));
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        dibujarMarcadores();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sucursalesRef != null && sucursalesListener != null) {
            sucursalesRef.removeEventListener(sucursalesListener);
        }
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
