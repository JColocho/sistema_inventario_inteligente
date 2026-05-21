package com.example.sistema_inventario_inteligente;

import android.os.Bundle;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.adapters.ProductoAdapter;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.example.sistema_inventario_inteligente.models.Sucursal;
import com.example.sistema_inventario_inteligente.models.SucursalContrato;
import com.example.sistema_inventario_inteligente.models.SucursalRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventarioFragment extends Fragment {

    private RecyclerView rvProducto;
    private Button btnAgregar;
    private TextInputEditText etBuscar;
    private ChipGroup chipGroupCategorias;
    private Chip chipTodo, chipComputadora, chipCelular, chipPeriferico,
            chipOtros, chipAudio, chipTelevisor, chipTableta;
    private TextView tvContadorProductos;

    private ProductoAdapter productoAdapter;
    private final ArrayList<Producto> listaProductos = new ArrayList<>();
    private final ArrayList<Producto> listaFiltrada  = new ArrayList<>();

    private Spinner spSucursalInventario;
    private SucursalRepository sucursalRepository;
    private FusedLocationProviderClient fusedLocationClient;
    private final ArrayList<Sucursal> listaSucursales = new ArrayList<>();
    private String sucursalSeleccionadaId = null;   // null = "Todas las sucursales"

    private ProductoContrato repositorio;
    private String categoriaActiva = "";
    private String textoBusqueda   = "";

    public InventarioFragment() {}

    public static InventarioFragment newInstance() {
        InventarioFragment fragment = new InventarioFragment();
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
        View view = inflater.inflate(R.layout.fragment_inventario, container, false);

        repositorio = new ProductoRepository();

        rvProducto = view.findViewById(R.id.rvProductos);
        btnAgregar = view.findViewById(R.id.btnAgregar);
        etBuscar = view.findViewById(R.id.etBuscar);
        chipGroupCategorias  = view.findViewById(R.id.chipGroupCategorias);
        tvContadorProductos  = view.findViewById(R.id.tvContadorProductos);

        chipTodo = view.findViewById(R.id.chipTodos);
        chipComputadora = view.findViewById(R.id.chipComputadoras);
        chipTableta = view.findViewById(R.id.chipTablets);
        chipCelular = view.findViewById(R.id.chipCelulares);
        chipPeriferico  = view.findViewById(R.id.chipPerifericos);
        chipOtros = view.findViewById(R.id.chipOtros);
        chipAudio = view.findViewById(R.id.chipAudio);
        chipTelevisor = view.findViewById(R.id.chipTelevisores);

        productoAdapter = new ProductoAdapter(listaFiltrada);
        rvProducto.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProducto.setAdapter(productoAdapter);

        spSucursalInventario = view.findViewById(R.id.spSucursalInventario);
        sucursalRepository = new SucursalRepository();
        fusedLocationClient  = LocationServices.getFusedLocationProviderClient(requireActivity());

        configurarSpinnerSucursales();

        // Filtro por texto de búsqueda
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim();
                aplicarFiltros();
            }
        });

        // Filtros por categoría
        chipGroupCategorias.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int chipId = checkedIds.get(0);

            if (chipId == chipTodo.getId()){
                categoriaActiva = "";
            }
            else if (chipId == chipComputadora.getId()){
                categoriaActiva = "Computadoras";
            }
            else if (chipId == chipCelular.getId()){
                categoriaActiva = "Smartphones";
            }
            else if (chipId == chipTableta.getId()){
                categoriaActiva = "Tablets";
            }
            else if (chipId == chipAudio.getId()){
                categoriaActiva = "Audio";
            }
            else if (chipId == chipTelevisor.getId()){
                categoriaActiva = "Televisores";
            }
            else if (chipId == chipPeriferico.getId()){
                categoriaActiva = "Perifericos";
            }
            else if (chipId == chipOtros.getId()){
                categoriaActiva = "Otro";
            }

            aplicarFiltros();
        });

        btnAgregar.setOnClickListener(v ->
                startActivity(new Intent(getContext(), AgregarProductoActivity.class)));

        cargarProductos();
        return view;
    }
    private void aplicarFiltros() {
        listaFiltrada.clear();
        String busqueda = etBuscar.getText() != null
                ? etBuscar.getText().toString().trim().toLowerCase()
                : "";

        for (Producto p : listaProductos) {

            //Filtro sucursal
            boolean pasaSucursal = sucursalSeleccionadaId == null
                    || (p.getIdSucursal() != null
                    && p.getIdSucursal().equals(sucursalSeleccionadaId));

            //Filtro categoría
            boolean pasaCategoria = categoriaActiva.isEmpty()
                    || (p.getCategoria() != null
                    && p.getCategoria().equals(categoriaActiva));

            //Filtro búsqueda
            boolean pasaBusqueda = busqueda.isEmpty()
                    || (p.getNombre() != null
                    && p.getNombre().toLowerCase().contains(busqueda));

            if (pasaSucursal && pasaCategoria && pasaBusqueda) {
                listaFiltrada.add(p);
            }
        }

        productoAdapter.notifyDataSetChanged();
        actualizarContador();
    }

    private void actualizarContador() {
        tvContadorProductos.setText(listaFiltrada.size() + " productos");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        repositorio.detenerEscucha();
    }

    private void cargarProductos() {
        repositorio.obtenerProductosEnTiempoReal("", new ProductoContrato.LeerCallback() {
            @Override
            public void onProductosCargados(List<Producto> productos) {
                listaProductos.clear();
                listaProductos.addAll(productos);
                Collections.reverse(listaProductos);
                aplicarFiltros();
            }

            @Override
            public void onError(String error) {
                if (getContext() != null)
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configurarSpinnerSucursales() {
        sucursalRepository.obtenerTodas(new SucursalContrato.LeerCallback() {
            @Override
            public void onSucursalesCargadas(List<Sucursal> sucursales) {
                if (getContext() == null) return;

                listaSucursales.clear();
                //Usando un objeto Sucursal con id null para distinguirla.
                Sucursal opcionTodas = new Sucursal();
                opcionTodas.setIdSucursal(null);
                opcionTodas.setNombre("Todas las sucursales");
                listaSucursales.add(opcionTodas);
                listaSucursales.addAll(sucursales);

                ArrayAdapter<Sucursal> adapter = new ArrayAdapter<>(
                        getContext(), R.layout.spinner_item, listaSucursales);
                adapter.setDropDownViewResource(R.layout.spinner_dropdown);
                spSucursalInventario.setAdapter(adapter);

                spSucursalInventario.setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view,
                                                       int position, long id) {
                                Sucursal seleccionada = (Sucursal) spSucursalInventario.getSelectedItem();
                                //null id = "Todas las sucursales"
                                sucursalSeleccionadaId = seleccionada.getIdSucursal();
                                aplicarFiltros();
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {}
                        });

                //Después de cargar el Spinner, pre-seleccionar la sucursal más cercana
                obtenerUbicacionSeleccionarCercana();
            }

            @Override
            public void onError(String error) {
                if (getContext() != null)
                    Toast.makeText(getContext(),
                            "Error al cargar sucursales: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressWarnings("MissingPermission")
    private void obtenerUbicacionSeleccionarCercana() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;  //Sin permiso: el Spinner queda en "Todas las sucursales"
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || listaSucursales.size() <= 1) return;

            Sucursal sucursalMasCercana = null;
            float distanciaMinima = Float.MAX_VALUE;
            float[] resultado = new float[1];

            //Empezar en índice 1 para saltar "Todas las sucursales"
            for (int i = 1; i < listaSucursales.size(); i++) {
                Sucursal s = listaSucursales.get(i);
                android.location.Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        s.getLatitud(), s.getLongitud(), resultado);

                if (resultado[0] < distanciaMinima) {
                    distanciaMinima = resultado[0];
                    sucursalMasCercana = s;
                }
            }

            if (sucursalMasCercana == null) return;

            //Buscando el objeto Sucursal directamente.
            int posicion = listaSucursales.indexOf(sucursalMasCercana);
            if (posicion >= 0) {
                spSucursalInventario.setSelection(posicion);
                // onItemSelectedListener se dispara automáticamente y llama a aplicarFiltros()
            }
        });
    }
}