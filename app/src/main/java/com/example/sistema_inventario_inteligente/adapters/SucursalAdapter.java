package com.example.sistema_inventario_inteligente.adapters;

import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sistema_inventario_inteligente.R;
import com.example.sistema_inventario_inteligente.databinding.ItemSucursalBinding;
import com.example.sistema_inventario_inteligente.models.Sucursal;

import java.util.ArrayList;
import java.util.List;

public class SucursalAdapter extends RecyclerView.Adapter<SucursalAdapter.ViewHolder>{
    private List<Sucursal> sucursales = new ArrayList<>();
    private double latUser = 0;
    private double lngUser = 0;
    private String sucursalActivaId = null;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemSucursalBinding binding;

        ViewHolder(ItemSucursalBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSucursalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Sucursal s = sucursales.get(position);

        holder.binding.tvNombreSucursal.setText(s.getNombre() != null ? s.getNombre() : "");
        holder.binding.tvDireccionSucursal.setText(s.getDireccion() != null ? s.getDireccion() : "");
        holder.binding.tvHorarioSucursal.setText(s.getHorario() != null ? s.getHorario() : "");

        if (latUser != 0 && lngUser != 0) {
            float[] resultado = new float[1];
            Location.distanceBetween(latUser, lngUser, s.getLatitud(), s.getLongitud(), resultado);
            float km = resultado[0] / 1000f;
            holder.binding.tvDistanciaSucursal.setText(String.format("%.1f km", km));
        } else {
            holder.binding.tvDistanciaSucursal.setText("— km");
        }

        boolean esActiva = sucursalActivaId != null && sucursalActivaId.equals(s.getIdSucursal());
        holder.binding.tvBadgeAqui.setVisibility(esActiva ? View.VISIBLE : View.GONE);
        int strokeColor = esActiva
                ? ContextCompat.getColor(holder.binding.getRoot().getContext(), R.color.accent)
                : ContextCompat.getColor(holder.binding.getRoot().getContext(), R.color.line);
        holder.binding.getRoot().setStrokeColor(strokeColor);
    }

    @Override
    public int getItemCount() {
        return sucursales.size();
    }

    public void actualizarLista(List<Sucursal> nueva, double lat, double lng) {
        sucursales = nueva;
        latUser = lat;
        lngUser = lng;
        notifyDataSetChanged();
    }

    public void actualizarLista(List<Sucursal> nueva) {
        actualizarLista(nueva, latUser, lngUser);
    }

    public void marcarSucursalActiva(String id) {
        sucursalActivaId = id;
        notifyDataSetChanged();
    }
}
