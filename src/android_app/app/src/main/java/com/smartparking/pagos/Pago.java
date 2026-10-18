package com.smartparking.pagos;

public class Pago {
    private int id;
    private String id_reserva;
    private String id_usuario;
    private String usuario_id;  // Agregue este campo
    private String id_estacionamiento;
    private double monto_total;
    private double subtotal;
    private String fecha_pago;

    public int getId() { return id; }
    public String getId_reserva() { return id_reserva; }
    public String getId_usuario() { return id_usuario; }
    public String getUsuario_id() { return usuario_id; }
    public String getId_estacionamiento() { return id_estacionamiento; }
    public double getMonto_total() { return monto_total; }
    public double getSubtotal() { return subtotal; }
    public String getFecha_pago() { return fecha_pago; }
}



