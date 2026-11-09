package com.smartparking.pagos;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Guarda en el teléfono lo que el usuario eligió para pagar la reserva actual:
 * el cupón aplicado y la tarjeta (si eligió una distinta de la predeterminada).
 *
 * Así no se pierde al cambiar de pestaña (el fragment se vuelve a crear) ni al cerrar la app.
 * Se guarda junto con el id de la reserva: si la reserva cambia, lo guardado ya no aplica.
 * Se borra al completar el pago o cuando la reserva expira.
 */
public final class PreferenciasPago {

    private static final String PREFS = "PagoEnCurso";
    private static final String KEY_RESERVA = "id_reserva";
    private static final String KEY_CUPON = "cupon";
    private static final String KEY_TARJETA = "id_tarjeta";

    private PreferenciasPago() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Si lo guardado es de otra reserva, se descarta. */
    private static SharedPreferences paraReserva(Context context, long idReserva) {
        SharedPreferences p = prefs(context);
        if (p.getLong(KEY_RESERVA, -1) != idReserva) {
            p.edit().clear().putLong(KEY_RESERVA, idReserva).apply();
        }
        return p;
    }

    @Nullable
    public static String getCupon(Context context, long idReserva) {
        return paraReserva(context, idReserva).getString(KEY_CUPON, null);
    }

    public static void setCupon(Context context, long idReserva, @Nullable String cupon) {
        SharedPreferences.Editor e = paraReserva(context, idReserva).edit();
        if (cupon == null) e.remove(KEY_CUPON); else e.putString(KEY_CUPON, cupon);
        e.apply();
    }

    /** La tarjeta elegida para esta reserva, o null si se usa la predeterminada. */
    @Nullable
    public static String getTarjeta(Context context, long idReserva) {
        return paraReserva(context, idReserva).getString(KEY_TARJETA, null);
    }

    public static void setTarjeta(Context context, long idReserva, @Nullable String idTarjeta) {
        SharedPreferences.Editor e = paraReserva(context, idReserva).edit();
        if (idTarjeta == null) e.remove(KEY_TARJETA); else e.putString(KEY_TARJETA, idTarjeta);
        e.apply();
    }

    /** Se llama al completar el pago o cuando la reserva expira. */
    public static void limpiar(Context context) {
        prefs(context).edit().clear().apply();
    }
}
