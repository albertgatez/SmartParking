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

    public MetodoPago(String id, String marca, String ultimos4, int mesExp, int anioExp) {
        this.id = id;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.mesExp = mesExp;
        this.anioExp = anioExp;
    }

    public String getId() { return id; }
    public String getMarca() { return marca; }
    public String getUltimos4() { return ultimos4; }
    public int getMesExp() { return mesExp; }
    public int getAnioExp() { return anioExp; }

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
