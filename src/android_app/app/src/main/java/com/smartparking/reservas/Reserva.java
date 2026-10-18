package com.smartparking.reservas;

import java.io.Serializable;

/**
 * Modelo que representa una reserva en el sistema
 */
public class Reserva implements Serializable {
    private int id;
    private String idUsuario;
    private int idCajon;

    private long idEstacionamiento;

    private String tiempoInicio; // timestamp ISO 8601
    private String tiempoFin; // timestamp ISO 8601
    private boolean isConfirmada; // false = pendiente, true = confirmada
    private String tokenEstacionamiento;
    private long expiracionMs; // timestamp en milisegundos para el temporizador local

    // Información adicional del cajón (para mostrar en la UI)
    private String numeroCajon;
    private String seccion;
    private boolean discapacitado;
    private String nombreEstacionamiento;
    private double precioHora;

    public Reserva() {
    }

    public Reserva(int id, String idUsuario, int idCajon, String tiempoInicio, String tiempoFin, boolean isConfirmada, String tokenEstacionamiento, long expiracionMs, String numeroCajon, String seccion, boolean discapacitado, String nombreEstacionamiento, double precioHora, long idEstacionamiento) {
        this.id = id;
        this.idUsuario = idUsuario;
        this.idCajon = idCajon;
        this.tiempoInicio = tiempoInicio;
        this.tiempoFin = tiempoFin;
        this.isConfirmada = isConfirmada;
        this.tokenEstacionamiento = tokenEstacionamiento;
        this.expiracionMs = expiracionMs;
        this.numeroCajon = numeroCajon;
        this.seccion = seccion;
        this.discapacitado = discapacitado;
        this.nombreEstacionamiento = nombreEstacionamiento;
        this.precioHora = precioHora;

        this.idEstacionamiento = idEstacionamiento;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(String idUsuario) {
        this.idUsuario = idUsuario;
    }

    public int getIdCajon() {
        return idCajon;
    }

    public void setIdCajon(int idCajon) {
        this.idCajon = idCajon;
    }

    public long getIdEstacionamiento() {
        return idEstacionamiento;
    }

    public void setIdEstacionamiento(long idEstacionamiento) {
        this.idEstacionamiento = idEstacionamiento;
    }

    public String getTiempoInicio() {
        return tiempoInicio;
    }

    public void setTiempoInicio(String tiempoInicio) {
        this.tiempoInicio = tiempoInicio;
    }

    public String getTiempoFin() {
        return tiempoFin;
    }

    public void setTiempoFin(String tiempoFin) {
        this.tiempoFin = tiempoFin;
    }

    public boolean isConfirmada() {
        return isConfirmada;
    }

    public void setConfirmada(boolean confirmada) {
        this.isConfirmada = confirmada;
    }

    public String getTokenEstacionamiento() {
        return tokenEstacionamiento;
    }

    public void setTokenEstacionamiento(String tokenEstacionamiento) {
        this.tokenEstacionamiento = tokenEstacionamiento;
    }

    public long getExpiracionMs() {
        return expiracionMs;
    }

    public void setExpiracionMs(long expiracionMs) {
        this.expiracionMs = expiracionMs;
    }

    public String getNumeroCajon() {
        return numeroCajon;
    }

    public void setNumeroCajon(String numeroCajon) {
        this.numeroCajon = numeroCajon;
    }

    public String getSeccion() {
        return seccion;
    }

    public void setSeccion(String seccion) {
        this.seccion = seccion;
    }

    public boolean isDiscapacitado() {
        return discapacitado;
    }

    public void setDiscapacitado(boolean discapacitado) {
        this.discapacitado = discapacitado;
    }

    public String getNombreEstacionamiento() {
        return nombreEstacionamiento;
    }

    public void setNombreEstacionamiento(String nombreEstacionamiento) {
        this.nombreEstacionamiento = nombreEstacionamiento;
    }

    public double getPrecioHora() {
        return precioHora;
    }

    public void setPrecioHora(double precioHora) {
        this.precioHora = precioHora;
    }
}