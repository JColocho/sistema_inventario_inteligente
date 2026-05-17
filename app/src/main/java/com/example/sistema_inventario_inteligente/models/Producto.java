package com.example.sistema_inventario_inteligente.models;

public class Producto {
    private String idProducto;
    private String nombre;
    private String categoria;
    private String descripcion;
    private Double precio;
    private Double cantidad;
    private Double coordX;
    private Double coordY;
    private Double coordZ;

    public String getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(String idProducto) {
        this.idProducto = idProducto;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Double getCantidad() {
        return cantidad;
    }

    public void setCantidad(Double cantidad) {
        this.cantidad = cantidad;
    }

    public Double getCoordX() {
        return coordX;
    }

    public void setCoordX(Double coordX) {
        this.coordX = coordX;
    }

    public Double getCoordY() {
        return coordY;
    }

    public void setCoordY(Double coordY) {
        this.coordY = coordY;
    }

    public Double getCoordZ() {
        return coordZ;
    }

    public void setCoordZ(Double coordZ) {
        this.coordZ = coordZ;
    }

    public Producto() {
    }

    public Producto(String nombre, String categoria, String descripcion, Double precio, Double cantidad, Double coordX, Double coordY, Double coordZ) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.precio = precio;
        this.cantidad = cantidad;
        this.coordX = coordX;
        this.coordY = coordY;
        this.coordZ = coordZ;
    }
}
