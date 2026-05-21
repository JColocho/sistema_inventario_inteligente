package com.example.sistema_inventario_inteligente;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.adapters.ProductoAdapater;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.example.sistema_inventario_inteligente.models.ProductoContrato;
import com.example.sistema_inventario_inteligente.models.ProductoRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class InventarioFragment extends Fragment {
    private RecyclerView rvProducto;
    private Button btnAgregar;
    private TextInputEditText etBuscar;
    private ChipGroup chipGroupCategorias;
    private Chip chipTodo, chipComputadora, chipCelular, chipPeriferico, chipOtros, chipAudio, chipTelevisor, chipTableta;
    private TextView tvContadorProductos;

    // Datos
    private ProductoAdapater productoAdapater;
    private final ArrayList<Producto> listaProductos = new ArrayList<>();

    // Lista filtrada de productos por categoria
    private final ArrayList<Producto> listaFiltrada = new ArrayList<>();

    private ProductoContrato repositorio;
    private String categoriaActiva = "";
    private String textoBusqueda = "";

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
        chipGroupCategorias = view.findViewById(R.id.chipGroupCategorias);
        tvContadorProductos = view.findViewById(R.id.tvContadorProductos);

        chipTodo = view.findViewById(R.id.chipTodos);
        chipComputadora = view.findViewById(R.id.chipComputadoras);
        chipTableta = view.findViewById(R.id.chipTablets);
        chipCelular = view.findViewById(R.id.chipCelulares);
        chipPeriferico = view.findViewById(R.id.chipPerifericos);
        chipOtros = view.findViewById(R.id.chipOtros);
        chipAudio = view.findViewById(R.id.chipAudio);
        chipTelevisor = view.findViewById(R.id.chipTelevisores);

        productoAdapater = new ProductoAdapater(listaFiltrada);
        rvProducto.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProducto.setAdapter(productoAdapater);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim();
                aplicarFiltros();
            }
        });

        //Filtros por categoria
        chipGroupCategorias.setOnCheckedStateChangeListener((group, checkedIds) -> {
            //Validando que este seleccionado un chip
            if (checkedIds.isEmpty()) return;

            int chipId = checkedIds.get(0);

            //Identificamos la categoría seleccionada
            if      (chipId == chipTodo.getId()){
                categoriaActiva = "";
            }
            else if (chipId == chipComputadora.getId()){
                categoriaActiva = "Computadoras";
            }
            else if (chipId == chipCelular.getId()){
                categoriaActiva = "Smartphones";
            }
            else if (chipId == chipTableta.getId()) {
                categoriaActiva = "Tablets";
            }
            else if (chipId == chipAudio.getId()) {
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

    //Metodo para filtrado de productos por categoria y nombre
    private void aplicarFiltros() {
        listaFiltrada.clear();

        for (Producto itemProducto : listaProductos) {

            //Filtro para categoria
            boolean pasaCategoria = categoriaActiva.isEmpty()
                    || (itemProducto.getCategoria() != null
                    && itemProducto.getCategoria().equalsIgnoreCase(categoriaActiva));

            //Filtro para busqueda por nombre
            boolean pasaBusqueda = textoBusqueda.isEmpty()
                    || (itemProducto.getNombre() != null
                    && itemProducto.getNombre().toLowerCase().contains(textoBusqueda.toLowerCase()));

            if (pasaCategoria && pasaBusqueda) {
                listaFiltrada.add(itemProducto);
            }
        }

        productoAdapater.notifyDataSetChanged();
        actualizarContador();
    }

    //Metodo para actualizar contador de productos
    private void actualizarContador() {
        tvContadorProductos.setText(listaFiltrada.size() + " productos");
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        repositorio.detenerEscucha();
    }

    //Metodo para cargar todos los productos desde Firebase
    private void cargarProductos() {
        //Cargando los productos en tiempo real
        repositorio.obtenerProductosEnTiempoReal("", new ProductoContrato.LeerCallback() {
            @Override
            public void onProductosCargados(List<Producto> productos) {
                //Limpiar lista
                listaProductos.clear();
                //Cargar lista con los productos actualizados
                listaProductos.addAll(productos);

                //Aplicar filtros
                aplicarFiltros();
            }

            @Override
            public void onError(String error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}