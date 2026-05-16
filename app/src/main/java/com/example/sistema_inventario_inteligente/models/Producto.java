package com.example.sistema_inventario_inteligente.models;

public class Producto {
    public String nombre;
    public String categoria;
    public String descripcion;
    public Double precio;
    public Double stock;

    public Producto() {
    }

    public Producto(String nombre, String categoria,String descripcion, Double precio, Double stock) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stock = stock;
    }
}
