package com.example.sistema_inventario_inteligente.models;

import java.util.List;

public class VectoresIA {
    private List<Double> vector;

    public VectoresIA() {}

    public VectoresIA(List<Double> vector) {
        this.vector = vector;
    }

    public List<Double> getVector() { return vector; }
    public void setVector(List<Double> vector) { this.vector = vector; }
}
