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

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.sistema_inventario_inteligente.DetalleProductoActivity;
import com.example.sistema_inventario_inteligente.R;
import com.example.sistema_inventario_inteligente.glide.GlideApp;
import com.example.sistema_inventario_inteligente.models.Producto;
import com.google.android.material.chip.Chip;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoVH> {

    public ArrayList<Producto> dataProductos;

    public ProductoAdapter(ArrayList<Producto> productos) {
        this.dataProductos = productos;
    }

    @NonNull
    @Override
    public ProductoVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_producto, parent, false);
        return new ProductoVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoVH holder, int position) {
        Producto producto = dataProductos.get(position);
        Context context = holder.itemView.getContext();

        StorageReference imageRef = FirebaseStorage.getInstance()
                .getReferenceFromUrl(producto.getUrlImagenProducto());

        GlideApp.with(context)
                .load(imageRef)
                .placeholder(R.drawable.camara)
                .error(R.drawable.camara)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(holder.imgProducto);

        holder.tvNombre.setText(producto.getNombre());
        holder.tvPrecio.setText(String.format("$%.2f", producto.getPrecio()));
        holder.tvUnidades.setText(producto.getCantidad().toString() + " Unidades");
        holder.chipCategoria.setText(producto.getCategoria());

        if (producto.getCantidad() > 0) {
            holder.chipStock.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.status_success_10)));
            holder.chipStock.setTextColor(ContextCompat.getColor(context, R.color.status_success));
            holder.chipStock.setText("En Stock");
        } else {
            holder.chipStock.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.status_error_10)));
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

    public static class ProductoVH extends RecyclerView.ViewHolder {
        public TextView tvNombre, tvPrecio, tvUnidades;
        public ImageView imgProducto, btnDetalle;
        public Chip chipCategoria, chipStock;

        public ProductoVH(@NonNull View itemView) {
            super(itemView);
            imgProducto   = itemView.findViewById(R.id.ivProducto);
            btnDetalle    = itemView.findViewById(R.id.ivDetalle);
            tvPrecio      = itemView.findViewById(R.id.tvPrecio);
            tvNombre      = itemView.findViewById(R.id.tvNombreProducto);
            tvUnidades    = itemView.findViewById(R.id.tvUnidades);
            chipCategoria = itemView.findViewById(R.id.chipCategoria);
            chipStock     = itemView.findViewById(R.id.chipStock);
        }
    }
}
