package com.smartparking.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utilidades de fecha y hora.
 * Toda la app trabaja con la hora local del estacionamiento (America/Mexico_City)
 * y guarda en la base de datos en UTC, sin importar la zona horaria del teléfono.
 */
public class DateTimeUtils {

    private static final String TIMEZONE_LOCAL = "America/Mexico_City"; // UTC-6
    private static final String TIMEZONE_UTC = "UTC";
    private static final String FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String FORMAT_PARSE = "yyyy-MM-dd'T'HH:mm:ss";

    private static TimeZone zonaLocal() {
        return TimeZone.getTimeZone(TIMEZONE_LOCAL);
    }

    private static String aIsoUTC(Date fecha) {
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_ISO, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone(TIMEZONE_UTC));
        return sdf.format(fecha);
    }

    /**
     * Fecha de hoy (hora local) a la hora indicada.
     * hora = 24 se interpreta como las 00:00 del día siguiente.
     */
    public static Date fechaHoyALaHora(int hora, int minuto) {
        Calendar calendar = Calendar.getInstance(zonaLocal());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.HOUR_OF_DAY, hora);
        calendar.add(Calendar.MINUTE, minuto);
        return calendar.getTime();
    }

    /**
     * Convierte una hora local de hoy a formato ISO 8601 en UTC para guardar en BD.
     * Ejemplo: 23:00 local -> 2025-11-24T05:00:00Z (UTC)
     */
    public static String convertirLocalAUTC(int hora, int minuto) {
        return aIsoUTC(fechaHoyALaHora(hora, minuto));
    }

    /** Inicio del día de hoy (00:00 hora local) en ISO UTC. */
    public static String inicioDelDiaUTC() {
        return aIsoUTC(fechaHoyALaHora(0, 0));
    }

    /** Fin del día de hoy (00:00 del día siguiente, hora local) en ISO UTC. */
    public static String finDelDiaUTC() {
        return aIsoUTC(fechaHoyALaHora(24, 0));
    }

    /** Momento actual en ISO UTC. */
    public static String ahoraUTC() {
        return aIsoUTC(new Date());
    }

    /** Hora actual (0-23) en la zona horaria del estacionamiento. */
    public static int horaActualLocal() {
        return Calendar.getInstance(zonaLocal()).get(Calendar.HOUR_OF_DAY);
    }

    /**
     * Parsea una fecha UTC de la BD.
     * Acepta "2025-11-23T05:00:00Z", "2025-11-23T05:00:00+00:00" o con milisegundos.
     */
    public static Date parsearUTCALocal(String fechaUTC) throws Exception {
        String fechaLimpia = fechaUTC.replace(" ", "T");
        if (fechaLimpia.length() > 19) {
            fechaLimpia = fechaLimpia.substring(0, 19);
        }

        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_PARSE, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone(TIMEZONE_UTC));
        return sdf.parse(fechaLimpia);
    }

    /** Formatea una fecha mostrando solo la hora local. Ejemplo: "23:00" */
    public static String formatearHoraLocal(Date fecha) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        sdf.setTimeZone(zonaLocal());
        return sdf.format(fecha);
    }

    /** Convierte una fecha ISO de la BD a texto local. Ejemplo: "23/11/2025 17:57" */
    public static String formatearFechaHoraLocal(String fechaUTC) {
        if (fechaUTC == null || fechaUTC.isEmpty()) return "Sin fecha";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            sdf.setTimeZone(zonaLocal());
            return sdf.format(parsearUTCALocal(fechaUTC));
        } catch (Exception e) {
            return fechaUTC;
        }
    }

    /** Hora del día (0-23) de una fecha, en la zona horaria del estacionamiento. */
    public static int obtenerHoraLocal(Date fecha) {
        Calendar calendar = Calendar.getInstance(zonaLocal());
        calendar.setTime(fecha);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    /** Agrega horas a una fecha UTC y devuelve el nuevo string en UTC. */
    public static String agregarHorasAUTC(String fechaUTC, int horas) throws Exception {
        Date fecha = parsearUTCALocal(fechaUTC);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(TIMEZONE_UTC));
        calendar.setTime(fecha);
        calendar.add(Calendar.HOUR_OF_DAY, horas);
        return aIsoUTC(calendar.getTime());
    }
}
