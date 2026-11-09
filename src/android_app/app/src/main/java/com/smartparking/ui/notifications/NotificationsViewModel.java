package com.smartparking.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.smartparking.login.SessionManager;
import com.smartparking.notificaciones.Notificacion;
import com.smartparking.notificaciones.NotificacionesApiClient;

import java.util.ArrayList;
import java.util.List;

public class NotificationsViewModel extends ViewModel {

    private final MutableLiveData<List<Notificacion>> notificaciones = new MutableLiveData<>();
    private final MutableLiveData<Boolean> cargando = new MutableLiveData<>(false);
    // Mensaje del último error; null si la última carga salió bien
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<List<Notificacion>> getNotificaciones() {
        return notificaciones;
    }

    public LiveData<Boolean> getCargando() {
        return cargando;
    }

    public LiveData<String> getError() {
        return error;
    }

    /** Para avisar a la pantalla si quitar o restaurar un aviso falló. */
    public interface ResultadoCallback {
        void onError(String mensaje);
    }

    /**
     * Quita el aviso de la lista de inmediato y lo marca como oculto en el servidor.
     * Si el servidor falla, el aviso vuelve a su lugar.
     * @return la posición que tenía, para poder deshacer.
     */
    public int ocultar(Notificacion aviso, ResultadoCallback callback) {
        List<Notificacion> actual = notificaciones.getValue();
        if (actual == null) return -1;
        int posicion = actual.indexOf(aviso);
        if (posicion < 0) return -1;

        List<Notificacion> nueva = new ArrayList<>(actual);
        nueva.remove(posicion);
        notificaciones.setValue(nueva);

        NotificacionesApiClient.cambiarOculta(aviso.getId(), true, new NotificacionesApiClient.AccionCallback() {
            @Override
            public void onSuccess() { }

            @Override
            public void onError(String mensaje) {
                insertar(aviso, posicion);
                callback.onError(mensaje);
            }
        });
        return posicion;
    }

    /** Deshacer: vuelve a mostrar el aviso en la posición que tenía. */
    public void restaurar(Notificacion aviso, int posicion, ResultadoCallback callback) {
        insertar(aviso, posicion);
        NotificacionesApiClient.cambiarOculta(aviso.getId(), false, new NotificacionesApiClient.AccionCallback() {
            @Override
            public void onSuccess() { }

            @Override
            public void onError(String mensaje) {
                List<Notificacion> actual = notificaciones.getValue();
                if (actual != null) {
                    List<Notificacion> nueva = new ArrayList<>(actual);
                    nueva.remove(aviso);
                    notificaciones.setValue(nueva);
                }
                callback.onError(mensaje);
            }
        });
    }

    private void insertar(Notificacion aviso, int posicion) {
        List<Notificacion> actual = notificaciones.getValue();
        List<Notificacion> nueva = actual != null ? new ArrayList<>(actual) : new ArrayList<>();
        if (nueva.contains(aviso)) return;
        nueva.add(Math.max(0, Math.min(posicion, nueva.size())), aviso);
        notificaciones.setValue(nueva);
    }

    /** Vuelve a pedir los avisos del usuario con sesión iniciada. */
    public void cargar() {
        String userId = SessionManager.getUserId();
        if (userId == null) {
            error.setValue("Sesión inválida");
            return;
        }

        cargando.setValue(true);
        NotificacionesApiClient.obtenerNotificaciones(userId, new NotificacionesApiClient.ListaCallback() {
            @Override
            public void onSuccess(List<Notificacion> lista) {
                error.setValue(null);
                notificaciones.setValue(lista);
                cargando.setValue(false);
            }

            @Override
            public void onError(String mensaje) {
                error.setValue(mensaje);
                cargando.setValue(false);
            }
        });
    }
}