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
    private final double descuento;
    private final double total;

    public DesglosePago(int horas, double subtotal, double comision, double porcentajeComision,
                        String cupon, double descuento, double total) {
        this.horas = horas;
        this.subtotal = subtotal;
        this.comision = comision;
        this.porcentajeComision = porcentajeComision;
        this.cupon = cupon;
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
                json.optDouble("descuento", 0),
                json.getDouble("total"));
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
