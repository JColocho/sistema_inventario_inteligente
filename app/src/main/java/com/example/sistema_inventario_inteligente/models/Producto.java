package com.example.sistema_inventario_inteligente.models;

public class Producto {
    private String idProducto;
    private String urlImagenProducto;
    private String urlModelo3D;
    private String nombre;
    private String categoria;
    private String descripcion;
    private Double precio;
    private Double cantidad;

    public String getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(String idProducto) {
        this.idProducto = idProducto;
    }

    public String getUrlImagenProducto() {
        return urlImagenProducto;
    }

    public void setUrlImagenProducto(String urlImagenProducto) {
        this.urlImagenProducto = urlImagenProducto;
    }

    public String getUrlModelo3D() {
        return urlModelo3D;
    }

    public void setUrlModelo3D(String urlModelo3D) {
        this.urlModelo3D = urlModelo3D;
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

    public Producto() {
    }

    public Producto(String urlImagenProducto, String urlModelo3D, String nombre, String categoria, String descripcion, Double precio, Double cantidad) {
        this.urlImagenProducto = urlImagenProducto;
        this.urlModelo3D = urlModelo3D;
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.precio = precio;
        this.cantidad = cantidad;
    }
}
