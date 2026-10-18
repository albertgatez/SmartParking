package com.smartparking.reservas;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartparking.utils.DateTimeUtils;
import com.smartparking.network.SupabaseClient;

/**
 * Repository Singleton para gestionar la reserva activa del usuario
 * Usa ReservaActivaPrefs como fuente de verdad y expone LiveData para reactividad
 */
public class ReservaRepository {
    private static final String TAG = "ReservaRepository";
    private static ReservaRepository instance;

    private final ReservaActivaPrefs prefs;
    private final MutableLiveData<Reserva> reservaLiveData;
    private final SupabaseClient supabaseClient;

    private ReservaRepository(Context context) {
        this.prefs = new ReservaActivaPrefs(context.getApplicationContext());
        this.reservaLiveData = new MutableLiveData<>();
        this.supabaseClient = new SupabaseClient();

        // Cargar reserva inicial desde SharedPreferences
        cargarReservaInicial();
    }

    /**
     * Obtiene la instancia única del Repository
     */
    public static synchronized ReservaRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ReservaRepository(context);
        }
        return instance;
    }

    /**
     * Retorna el LiveData para que los Fragments/Activities se suscriban
     */
    public LiveData<Reserva> getReservaActiva() {
        return reservaLiveData;
    }

    /**
     * Carga la reserva inicial desde SharedPreferences
     */
    private void cargarReservaInicial() {
        int reservaId = prefs.getId();

        if (reservaId != -1) {
            Reserva reserva = prefs.getReservaActiva();
            Log.d(TAG, "Reserva cargada desde SharedPreferences: ID=" + reserva.getId());
            reservaLiveData.postValue(reserva);
        } else {
            Log.d(TAG, "No hay reserva guardada en SharedPreferences");
            reservaLiveData.postValue(null);
        }
    }

    /**
     * Guarda una nueva reserva y notifica a todos los observadores
     */
    public void guardarReserva(Reserva reserva) {
        Log.d(TAG, "Guardando reserva: ID=" + reserva.getId() + ", Confirmada=" + reserva.isConfirmada());

        prefs.copiarReserva(reserva);
        reservaLiveData.postValue(reserva);

        Log.d(TAG, "Reserva guardada exitosamente en SharedPreferences");
    }

    /**
     * Limpia la reserva actual y notifica a todos los observadores
     * Opcionalmente elimina la reserva de la base de datos
     */
    public void limpiarReserva(boolean eliminarDeBD) {
        int reservaId = prefs.getId();

        Log.d(TAG, "Limpiando reserva: ID=" + reservaId + ", eliminarDeBD=" + eliminarDeBD);

        // Limpiar SharedPreferences
        prefs.limpiar();
        reservaLiveData.postValue(null);

        // Si se solicita, eliminar de la base de datos
        if (eliminarDeBD && reservaId != -1) {
            supabaseClient.eliminarReserva(reservaId, new SupabaseClient.SupabaseCallback() {
                @Override
                public void onSuccessUsuario() {
                    Log.d(TAG, "Reserva eliminada de la BD exitosamente");
                }

                @Override
                public void onErrorUsuario(String message) {
                    Log.e(TAG, "Error al eliminar reserva de BD: " + message);
                }
            });
        }

        Log.d(TAG, "Reserva limpiada de SharedPreferences");
    }

    /**
     * Verifica si la reserva actual ya no es válida y la limpia:
     * - Pendiente: expira a los 5 minutos si no se pagó (se elimina también de la BD).
     * - Confirmada: termina cuando pasa su tiempo_fin (se conserva en la BD como historial).
     * @return true si había una reserva y expiró
     */
    public boolean verificarExpiracion() {
        Reserva reserva = prefs.getReservaActiva();

        if (reserva == null || reserva.getId() == -1) {
            return false;
        }

        long ahora = System.currentTimeMillis();

        if (reserva.isConfirmada()) {
            try {
                long fin = DateTimeUtils.parsearUTCALocal(reserva.getTiempoFin()).getTime();
                if (ahora >= fin) {
                    Log.d(TAG, "La reserva confirmada ya terminó; se limpia localmente");
                    limpiarReserva(false);
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "No se pudo leer tiempo_fin de la reserva", e);
            }
            return false;
        }

        long expiracionMs = reserva.getExpiracionMs();
        if (expiracionMs > 0 && ahora > expiracionMs) {
            Log.d(TAG, "Reserva pendiente expirada; se elimina");
            limpiarReserva(true);
            return true;
        }
        return false;
    }

    /**
     * Sincroniza la reserva local con la del servidor (por ejemplo, al iniciar sesión en
     * otro teléfono o al reinstalar la app).
     * - Si en el servidor hay una reserva confirmada vigente y localmente no, se recupera.
     * - Si localmente hay una reserva confirmada que ya no existe en el servidor, se limpia.
     * Las reservas pendientes no se recuperan porque su tiempo para pagar vive en el teléfono.
     */
    public void sincronizarConServidor(String idUsuario) {
        if (idUsuario == null) return;

        supabaseClient.verificarReservaActiva(idUsuario, new SupabaseClient.SupabaseCallbackReservaActiva() {
            @Override
            public void onSuccess(Reserva remota) {
                Reserva local = prefs.getReservaActiva();
                boolean hayLocal = local != null && local.getId() != -1;

                if (remota != null && remota.isConfirmada()) {
                    if (!hayLocal || local.getId() != remota.getId()) {
                        Log.d(TAG, "Recuperando reserva confirmada del servidor: ID=" + remota.getId());
                        guardarReserva(remota);
                    } else if (remota.getTiempoFin() != null && !remota.getTiempoFin().equals(local.getTiempoFin())) {
                        // Puede haberse extendido desde otro dispositivo
                        local.setTiempoFin(remota.getTiempoFin());
                        guardarReserva(local);
                    }
                } else if (hayLocal && local.isConfirmada()) {
                    Log.d(TAG, "La reserva confirmada local ya no está vigente en el servidor; se limpia");
                    limpiarReserva(false);
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "No se pudo sincronizar la reserva: " + message);
            }
        });
    }

    /**
     * Confirma la reserva actual (actualiza estado a confirmada)
     */
    public void confirmarReservaActual() {
        Reserva reserva = prefs.getReservaActiva();

        if (reserva == null || reserva.getId() == -1) {
            Log.e(TAG, "No hay reserva para confirmar");
            return;
        }

        Log.d(TAG, "Confirmando reserva: ID=" + reserva.getId());

        // Actualizar en la BD
        supabaseClient.confirmarReserva(reserva.getId(), new SupabaseClient.SupabaseCallback() {
            @Override
            public void onSuccessUsuario() {
                Log.d(TAG, "Reserva confirmada en BD exitosamente");

                // Actualizar en SharedPreferences
                reserva.setConfirmada(true);
                reserva.setExpiracionMs(0); // Sin expiración para reservas confirmadas

                prefs.copiarReserva(reserva);
                reservaLiveData.postValue(reserva);

                Log.d(TAG, "Reserva actualizada en SharedPreferences como confirmada");
            }

            @Override
            public void onErrorUsuario(String message) {
                Log.e(TAG, "Error al confirmar reserva en BD: " + message);
            }
        });
    }

    /**
     * Obtiene la reserva actual de forma síncrona (sin LiveData)
     * Útil para verificaciones rápidas
     */
    public Reserva getReservaSincrona() {
        return prefs.getReservaActiva();
    }

    /**
     * Verifica si hay una reserva activa (pendiente o confirmada)
     */
    public boolean tieneReservaActiva() {
        verificarExpiracion();
        Reserva reserva = prefs.getReservaActiva();
        return reserva != null && reserva.getId() != -1;
    }

    /**
     * Verifica si la reserva actual está pendiente de pago
     */
    public boolean reservaEsPendiente() {
        verificarExpiracion();
        Reserva reserva = prefs.getReservaActiva();
        return reserva != null && reserva.getId() != -1 && !reserva.isConfirmada();
    }

    /**
     * Verifica si la reserva actual está confirmada
     */
    public boolean reservaEstaConfirmada() {
        Reserva reserva = prefs.getReservaActiva();
        return reserva != null && reserva.getId() != -1 && reserva.isConfirmada();
    }
}