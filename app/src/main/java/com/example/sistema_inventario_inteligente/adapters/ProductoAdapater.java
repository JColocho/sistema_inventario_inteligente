package com.example.sistema_inventario_inteligente.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.sistema_inventario_inteligente.DetalleProductoActivity;
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
        Context context = holder.itemView.getContext();

        idProducto = producto.getIdProducto();

        Glide.with(context)
                .load(producto.getUrlImagenProducto())
                .placeholder(R.drawable.camara) // imagen mientras carga
                .error(R.drawable.camara)        // imagen si falla
                .centerCrop()
                .into(holder.imgProducto);

        holder.tvNombre.setText(producto.getNombre());
        holder.tvPrecio.setText(String.format("$%.2f", producto.getPrecio()));
        holder.tvUnidades.setText(producto.getCantidad().toString() + " Unidades");
        holder.chipategoria.setText(producto.getCategoria());

        if (producto.getCantidad() > 0){
            holder.chipStock.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_success_10)));
            holder.chipStock.setTextColor(ContextCompat.getColor(context, R.color.status_success));
            holder.chipStock.setText("En Stock");
        }
        else {
            holder.chipStock.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_error_10)));
            holder.chipStock.setTextColor(ContextCompat.getColor(context, R.color.text_error));
            holder.chipStock.setText("Sin Stock");
        }

        holder.btnDetalle.setOnClickListener(v -> {
            Intent intent = new Intent(context, DetalleProductoActivity.class);
            intent.putExtra("idProducto", producto.getIdProducto());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return dataProductos.size();
    }

    public class ProductoVH extends RecyclerView.ViewHolder {
        public TextView tvNombre,tvPrecio, tvUnidades;
        public ImageView imgProducto, btnDetalle;
        public Chip chipategoria, chipStock;
        public ProductoVH(@NonNull View itemView) {
            super(itemView);
            imgProducto = itemView.findViewById(R.id.ivProducto);
            btnDetalle = itemView.findViewById(R.id.ivDetalle);
            tvPrecio = itemView.findViewById(R.id.tvPrecio);
            tvNombre = itemView.findViewById(R.id.tvNombreProducto);
            tvUnidades = itemView.findViewById(R.id.tvUnidades);
            chipategoria = itemView.findViewById(R.id.chipCategoria);
            chipStock = itemView.findViewById(R.id.chipStock);
        }
    }
}
