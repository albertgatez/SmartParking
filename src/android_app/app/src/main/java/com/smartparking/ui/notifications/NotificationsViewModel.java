package com.smartparking.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.smartparking.login.SessionManager;
import com.smartparking.notificaciones.Notificacion;
import com.smartparking.notificaciones.NotificacionesApiClient;

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