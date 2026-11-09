package com.smartparking.pagos;

import android.content.Context;

import com.smartparking.utils.DateTimeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genera el historial de pagos como CSV para hojas de cálculo.
 * - UTF-8 con BOM: Excel reconoce los acentos.
 * - Montos con punto decimal y dos decimales; fechas en hora local.
 * - Separador de fila CRLF, que es el que usa Excel.
 */
public final class ExportadorPagos {

    public static final String NOMBRE_ARCHIVO = "historial_pagos.csv";
    private static final String CARPETA = "exports";
    private static final String BOM = "﻿";
    private static final String[] ENCABEZADOS = {
            "Fecha", "Estacionamiento", "Cajón", "Tipo",
            "Subtotal", "Comisión", "Descuento", "Total", "ID de pago"
    };

    private ExportadorPagos() {
    }

    /**
     * @param pagos              pagos del usuario, ya ordenados
     * @param nombresEstacionamiento id del estacionamiento -> nombre
     * @param cajonPorReserva    id de la reserva -> "Sección X - Cajón Y"
     */
    public static String generarCsv(List<Pago> pagos,
                                    Map<String, String> nombresEstacionamiento,
                                    Map<String, String> cajonPorReserva) {
        StringBuilder csv = new StringBuilder(BOM);
        agregarFila(csv, ENCABEZADOS);

        for (Pago pago : pagos) {
            String nombre = nombresEstacionamiento.get(pago.getId_estacionamiento());
            String cajon = cajonPorReserva.get(pago.getId_reserva());
            // Los pagos anteriores a la migración no tienen desglose: se dejan vacíos
            boolean tieneDesglose = pago.getSubtotal() > 0;

            agregarFila(csv,
                    DateTimeUtils.formatearFechaHoraLocal(pago.getFecha_pago()),
                    nombre != null ? nombre : "Estacionamiento " + pago.getId_estacionamiento(),
                    cajon != null ? cajon : "",
                    etiquetaTipo(pago.getTipo()),
                    tieneDesglose ? numero(pago.getSubtotal()) : "",
                    tieneDesglose ? numero(pago.getComision()) : "",
                    tieneDesglose ? numero(pago.getDescuento()) : "",
                    numero(pago.getMonto_total()),
                    String.valueOf(pago.getId()));
        }
        return csv.toString();
    }

    /** Guarda el CSV en la caché de la app (el FileProvider lo comparte con otras apps). */
    public static File escribir(Context context, String csv) throws IOException {
        File carpeta = new File(context.getCacheDir(), CARPETA);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de exportación");
        }
        File archivo = new File(carpeta, NOMBRE_ARCHIVO);
        try (OutputStream salida = new FileOutputStream(archivo, false)) {
            salida.write(csv.getBytes(StandardCharsets.UTF_8));
        }
        return archivo;
    }

    private static void agregarFila(StringBuilder csv, String... campos) {
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(escapar(campos[i]));
        }
        csv.append("\r\n");
    }

    /** Encierra entre comillas un campo que tenga comas, comillas o saltos de línea. */
    private static String escapar(String valor) {
        if (valor == null) return "";
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    private static String numero(double valor) {
        return String.format(Locale.US, "%.2f", valor);
    }

    private static String etiquetaTipo(String tipo) {
        if ("extension".equals(tipo)) return "Extensión de tiempo";
        if ("reserva".equals(tipo)) return "Reserva";
        return "Pago";
    }
}
