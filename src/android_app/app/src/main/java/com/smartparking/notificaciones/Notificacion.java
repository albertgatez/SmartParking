package com.smartparking.notificaciones;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de la tabla Notificacion (historial de avisos del usuario).
 * Tipos que genera el servidor: reserva_confirmada, reserva_extendida, recordatorio_fin,
 * cajon_reservado, cajon_ocupado, cajon_desocupado.
 */
public class Notificacion {
    private long id;
    @SerializedName("id_reserva")
    private Long idReserva;      // puede ser null
    private String tipo;
    private String titulo;
    private String mensaje;
    private boolean leida;
    @SerializedName("created_at")
    private String createdAt;    // timestamptz en UTC

    public long getId() { return id; }
    public Long getIdReserva() { return idReserva; }
    public String getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getMensaje() { return mensaje; }
    public boolean isLeida() { return leida; }
    public String getCreatedAt() { return createdAt; }
}