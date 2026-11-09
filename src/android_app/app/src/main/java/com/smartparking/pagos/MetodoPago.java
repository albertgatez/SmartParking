package com.smartparking.pagos;

import java.util.Locale;

/**
 * Tarjeta guardada en Stripe. Solo se reciben datos que se pueden mostrar
 * (marca, últimos 4 dígitos y vencimiento); el número completo nunca llega a la app.
 */
public class MetodoPago {

    private final String id;
    private final String marca;
    private final String ultimos4;
    private final int mesExp;
    private final int anioExp;
    private final boolean predeterminada;

    public MetodoPago(String id, String marca, String ultimos4, int mesExp, int anioExp, boolean predeterminada) {
        this.id = id;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.mesExp = mesExp;
        this.anioExp = anioExp;
        this.predeterminada = predeterminada;
    }

    public String getId() { return id; }
    public String getMarca() { return marca; }
    public String getUltimos4() { return ultimos4; }
    public int getMesExp() { return mesExp; }
    public int getAnioExp() { return anioExp; }
    /** true si es la tarjeta predeterminada del usuario (se guarda en Stripe). */
    public boolean isPredeterminada() { return predeterminada; }

    /** Texto corto para mostrar, por ejemplo "Visa •••• 4242 (vence 12/28)". */
    public String getDescripcion() {
        return getMarcaVisible() + " •••• " + ultimos4 + " (vence " + getVencimiento() + ")";
    }

    /** La predeterminada de la lista; si ninguna lo es, la primera; null si la lista está vacía. */
    public static MetodoPago buscarPredeterminada(java.util.List<MetodoPago> metodos) {
        if (metodos == null || metodos.isEmpty()) return null;
        for (MetodoPago m : metodos) {
            if (m.isPredeterminada()) return m;
        }
        return metodos.get(0);
    }

    /** Texto de la fila PAGO del Carrito y del diálogo de extensión. */
    public static String textoFilaPago(MetodoPago seleccionado) {
        if (seleccionado != null) return seleccionado.getDescripcion();
        return "Sin tarjeta guardada\nToca para agregar una";
    }

    /** Busca una tarjeta por id en la lista; null si ya no está. */
    public static MetodoPago buscarPorId(java.util.List<MetodoPago> metodos, String id) {
        if (metodos == null || id == null) return null;
        for (MetodoPago m : metodos) {
            if (id.equals(m.getId())) return m;
        }
        return null;
    }

    /** Nombre de la marca para mostrar, a partir del código de Stripe ("visa", "mastercard", ...). */
    public String getMarcaVisible() {
        if (marca == null || marca.isEmpty()) return "Tarjeta";
        switch (marca) {
            case "visa": return "Visa";
            case "mastercard": return "Mastercard";
            case "amex": return "American Express";
            case "discover": return "Discover";
            case "diners": return "Diners Club";
            case "jcb": return "JCB";
            case "unionpay": return "UnionPay";
            default: return marca.substring(0, 1).toUpperCase(Locale.getDefault()) + marca.substring(1);
        }
    }

    /** Vencimiento en formato MM/AA. */
    public String getVencimiento() {
        return String.format(Locale.getDefault(), "%02d/%02d", mesExp, anioExp % 100);
    }
}
