package com.smartparking.estacionamientos;

import java.io.Serializable;

/**
 * Modelo que representa un cajón de estacionamiento
 */
public class Cajon implements Serializable {
    private int id;
    private String numeroCajon;
    private boolean discapacitado;
    private String seccion;
    private int idEstacionamiento;
    private String estadoCajon; // puede ser "disponible", "ocupado", "mantenimiento", etc.

    public Cajon() {
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNumeroCajon() {
        return numeroCajon;
    }

    public void setNumeroCajon(String numeroCajon) {
        this.numeroCajon = numeroCajon;
    }

    public boolean isDiscapacitado() {
        return discapacitado;
    }

    public void setDiscapacitado(boolean discapacitado) {
        this.discapacitado = discapacitado;
    }

    public String getSeccion() {
        return seccion;
    }

    public void setSeccion(String seccion) {
        this.seccion = seccion;
    }

    public int getIdEstacionamiento() {
        return idEstacionamiento;
    }

    public void setIdEstacionamiento(int idEstacionamiento) {
        this.idEstacionamiento = idEstacionamiento;
    }

    public String getEstadoCajon() {
        return estadoCajon;
    }

    public void setEstadoCajon(String estadoCajon) {
        this.estadoCajon = estadoCajon;
    }

    /**
     * Retorna el identificador completo del cajón (ej: "A1")
     */
    public String getIdentificadorCompleto() {
        return seccion + numeroCajon;
    }
}