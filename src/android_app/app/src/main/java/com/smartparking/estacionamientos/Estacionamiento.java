package com.smartparking.estacionamientos;

import java.io.Serializable;

public class Estacionamiento implements Serializable {
    private int id;
    private String nombre;
    private double precioHora;
    private double latitud;
    private double longitud;

    // Constructor vacío
    public Estacionamiento() {
    }

    // Constructor completo
    public Estacionamiento(int id, String nombre, double precioHora, double latitud, double longitud) {
        this.id = id;
        this.nombre = nombre;
        this.precioHora = precioHora;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public double getPrecioHora() {
        return precioHora;
    }

    public void setPrecioHora(double precioHora) {
        this.precioHora = precioHora;
    }

    public double getLatitud() {
        return latitud;
    }

    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }
}