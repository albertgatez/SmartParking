package com.smartparking.pagos;

public class Pago {
    private int id;
    private String id_reserva;
    private String id_usuario;
    private String usuario_id;
    private String id_estacionamiento;
    private String tipo;                  // "reserva" o "extension"; null en pagos anteriores a la migración
    private double subtotal;
    private double comision;
    private double descuento;
    private double monto_total;
    private String fecha_pago;
    private String stripe_payment_intent;

    public int getId() { return id; }
    public String getId_reserva() { return id_reserva; }
    public String getId_usuario() { return id_usuario; }
    public String getUsuario_id() { return usuario_id; }
    public String getId_estacionamiento() { return id_estacionamiento; }
    public String getTipo() { return tipo; }
    public double getSubtotal() { return subtotal; }
    public double getComision() { return comision; }
    public double getDescuento() { return descuento; }
    public double getMonto_total() { return monto_total; }
    public String getFecha_pago() { return fecha_pago; }
    public String getStripe_payment_intent() { return stripe_payment_intent; }
}
