package com.smartparking.pagos;

import org.json.JSONObject;

/**
 * Desglose del cobro calculado por el servidor (Edge Function crear-pago-reserva-v2).
 * La app solo lo muestra; no calcula precios.
 */
public class DesglosePago {

    private final int horas;
    private final double subtotal;
    private final double comision;
    private final double porcentajeComision;
    private final String cupon;
    private final String cuponDescripcion;
    private final double descuento;
    private final double total;

    public DesglosePago(int horas, double subtotal, double comision, double porcentajeComision,
                        String cupon, String cuponDescripcion, double descuento, double total) {
        this.horas = horas;
        this.subtotal = subtotal;
        this.comision = comision;
        this.porcentajeComision = porcentajeComision;
        this.cupon = cupon;
        this.cuponDescripcion = cuponDescripcion;
        this.descuento = descuento;
        this.total = total;
    }

    static DesglosePago desdeJson(JSONObject json) throws Exception {
        return new DesglosePago(
                json.getInt("horas"),
                json.getDouble("subtotal"),
                json.getDouble("comision"),
                json.optDouble("porcentaje_comision", 0),
                json.isNull("cupon") ? null : json.getString("cupon"),
                json.isNull("cupon_descripcion") ? null : json.optString("cupon_descripcion", null),
                json.optDouble("descuento", 0),
                json.getDouble("total"));
    }

    /**
     * Descripción breve del cupón para mostrarla al usuario. Si el servidor no la manda
     * (versión anterior de la función), se arma con el porcentaje de descuento.
     */
    public String getCuponDescripcion() {
        if (cuponDescripcion != null && !cuponDescripcion.isEmpty()) return cuponDescripcion;
        if (cupon == null || subtotal <= 0) return null;
        long porcentaje = Math.round(descuento / subtotal * 100);
        return porcentaje + " % de descuento en el precio del estacionamiento.";
    }

    public int getHoras() { return horas; }
    public double getSubtotal() { return subtotal; }
    public double getComision() { return comision; }
    public double getPorcentajeComision() { return porcentajeComision; }
    /** Código del cupón aplicado, o null si no se aplicó ninguno. */
    public String getCupon() { return cupon; }
    public double getDescuento() { return descuento; }
    public double getTotal() { return total; }
}
