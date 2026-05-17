package com.example.sistema_inventario_inteligente.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.R;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;

public class ProductoAdapater extends RecyclerView.Adapter<ProductoAdapater.ProductoVH> {
    private String idProducto;
    public ArrayList<Producto> dataProductos = new ArrayList<>();

    public ProductoAdapater(ArrayList<Producto> productos) {
        this.dataProductos = productos;
    }

    @NonNull
    @Override
    public ProductoAdapater.ProductoVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_producto, parent, false);
        return new ProductoVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoAdapater.ProductoVH holder, int position) {
        Producto producto = dataProductos.get(position);

        idProducto = producto.getIdProducto();

        holder.tvNombre.setText(producto.getNombre());
        holder.tvPrecio.setText(String.format("$%.2f", producto.getPrecio()));
        holder.tvUnidades.setText(producto.getCantidad().toString() + " Unidades");
        holder.chipategoria.setText(producto.getCategoria());
    }

    @Override
    public int getItemCount() {
        return dataProductos.size();
    }

    public class ProductoVH extends RecyclerView.ViewHolder {
        public TextView tvNombre,tvPrecio, tvUnidades;
        public Chip chipategoria;
        public ProductoVH(@NonNull View itemView) {
            super(itemView);
            tvPrecio = itemView.findViewById(R.id.tvPrecio);
            tvNombre = itemView.findViewById(R.id.tvNombreProducto);
            tvUnidades = itemView.findViewById(R.id.tvUnidades);
            chipategoria = itemView.findViewById(R.id.chipCategoria);
        }
    }
}
