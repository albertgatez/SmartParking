package com.smartparking.reservas;

import android.content.Context;
import android.content.SharedPreferences;

public class ReservaActivaPrefs {

    private static final String PREFS_NAME = "ReservaPrefs";

    private static final String KEY_ID = "id";
    private static final String KEY_ID_USUARIO = "idUsuario";
    private static final String KEY_ID_CAJON = "idCajon";
    private static final String KEY_TIEMPO_INICIO = "tiempoInicio";
    private static final String KEY_TIEMPO_FIN = "tiempoFin";
    private static final String KEY_CONFIRMADA = "confirmada";
    private static final String KEY_TOKEN = "tokenEstacionamiento";
    private static final String KEY_EXPIRACION = "expiracionMs";

    private static final String KEY_NUMERO_CAJON = "numeroCajon";
    private static final String KEY_SECCION = "seccion";
    private static final String KEY_DISCAPACITADO = "discapacitado";
    private static final String KEY_NOMBRE_EST = "nombreEstacionamiento";
    private static final String KEY_PRECIO_HORA = "precioHora";
    private static final String KEY_ID_ESTACIONAMIENTO = "idEstacionamiento";

    private final SharedPreferences prefs;

    public ReservaActivaPrefs(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // GETTERS Y SETTERS

    public int getId() {
        return prefs.getInt(KEY_ID, -1);
    }

    public void setId(int value) {
        prefs.edit().putInt(KEY_ID, value).apply();
    }

    public String getIdUsuario() {
        return prefs.getString(KEY_ID_USUARIO, null);
    }

    public void setIdUsuario(String value) {
        prefs.edit().putString(KEY_ID_USUARIO, value).apply();
    }

    public int getIdCajon() {
        return prefs.getInt(KEY_ID_CAJON, -1);
    }

    public void setIdCajon(int value) {
        prefs.edit().putInt(KEY_ID_CAJON, value).apply();
    }

    public long getIdEstacionamiento() {
        // Usamos long ya que en la lógica de pago se maneja como long.
        return prefs.getLong(KEY_ID_ESTACIONAMIENTO, 0L);
    }

    public void setIdEstacionamiento(long value) {
        prefs.edit().putLong(KEY_ID_ESTACIONAMIENTO, value).apply();
    }

    public String getTiempoInicio() {
        return prefs.getString(KEY_TIEMPO_INICIO, null);
    }

    public void setTiempoInicio(String value) {
        prefs.edit().putString(KEY_TIEMPO_INICIO, value).apply();
    }

    public String getTiempoFin() {
        return prefs.getString(KEY_TIEMPO_FIN, null);
    }

    public void setTiempoFin(String value) {
        prefs.edit().putString(KEY_TIEMPO_FIN, value).apply();
    }

    public boolean isConfirmada() {
        return prefs.getBoolean(KEY_CONFIRMADA, false);
    }

    public void setEstado(boolean value) {
        prefs.edit().putBoolean(KEY_CONFIRMADA, value).apply();
    }

    public String getTokenEstacionamiento() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void setTokenEstacionamiento(String value) {
        prefs.edit().putString(KEY_TOKEN, value).apply();
    }

    public long getExpiracionMs() {
        return prefs.getLong(KEY_EXPIRACION, 0L);
    }

    public void setExpiracionMs(long value) {
        prefs.edit().putLong(KEY_EXPIRACION, value).apply();
    }

    public String getNumeroCajon() {
        return prefs.getString(KEY_NUMERO_CAJON, null);
    }

    public void setNumeroCajon(String value) {
        prefs.edit().putString(KEY_NUMERO_CAJON, value).apply();
    }

    public String getSeccion() {
        return prefs.getString(KEY_SECCION, null);
    }

    public void setSeccion(String value) {
        prefs.edit().putString(KEY_SECCION, value).apply();
    }

    public boolean isDiscapacitado() {
        return prefs.getBoolean(KEY_DISCAPACITADO, false);
    }

    public void setDiscapacitado(boolean value) {
        prefs.edit().putBoolean(KEY_DISCAPACITADO, value).apply();
    }

    public String getNombreEstacionamiento() {
        return prefs.getString(KEY_NOMBRE_EST, null);
    }

    public void setNombreEstacionamiento(String value) {
        prefs.edit().putString(KEY_NOMBRE_EST, value).apply();
    }

    public double getPrecioHora() {
        return Double.longBitsToDouble(
                prefs.getLong(KEY_PRECIO_HORA, Double.doubleToRawLongBits(0))
        );
    }

    public void setPrecioHora(double value) {
        prefs.edit().putLong(KEY_PRECIO_HORA, Double.doubleToRawLongBits(value)).apply();
    }


    // MÉTODOS ÚTILES


    /** Guarda todos los valores de un objeto Reserva */
    public void copiarReserva(Reserva r) {
        SharedPreferences.Editor e = prefs.edit();

        e.putInt(KEY_ID, r.getId());
        e.putString(KEY_ID_USUARIO, r.getIdUsuario());
        e.putInt(KEY_ID_CAJON, r.getIdCajon());
        e.putLong(KEY_ID_ESTACIONAMIENTO, r.getIdEstacionamiento());

        e.putString(KEY_TIEMPO_INICIO, r.getTiempoInicio());
        e.putString(KEY_TIEMPO_FIN, r.getTiempoFin());
        e.putBoolean(KEY_CONFIRMADA, r.isConfirmada());
        e.putString(KEY_TOKEN, r.getTokenEstacionamiento());
        e.putLong(KEY_EXPIRACION, r.getExpiracionMs());
        e.putString(KEY_NUMERO_CAJON, r.getNumeroCajon());
        e.putString(KEY_SECCION, r.getSeccion());
        e.putBoolean(KEY_DISCAPACITADO, r.isDiscapacitado());
        e.putString(KEY_NOMBRE_EST, r.getNombreEstacionamiento());
        e.putLong(KEY_PRECIO_HORA, Double.doubleToRawLongBits(r.getPrecioHora()));

        e.apply();
    }

    /** Construye un objeto Reserva desde SharedPreferences */
    public Reserva getReservaActiva() {
        // Debes verificar que la Reserva tiene un constructor que acepta todos estos parámetros,
        // incluyendo el nuevo ID del estacionamiento.
        return new Reserva(
                getId(),
                getIdUsuario(),
                getIdCajon(),
                getTiempoInicio(),
                getTiempoFin(),
                isConfirmada(),
                getTokenEstacionamiento(),
                getExpiracionMs(),
                getNumeroCajon(),
                getSeccion(),
                isDiscapacitado(),
                getNombreEstacionamiento(),
                getPrecioHora(),
                getIdEstacionamiento()
        );
    }

    /** Borra toda la información de la reserva */
    public void limpiar() {
        prefs.edit().clear().apply();
    }
}