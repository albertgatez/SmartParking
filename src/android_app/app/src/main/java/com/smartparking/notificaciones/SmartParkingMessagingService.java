package com.smartparking.notificaciones;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smartparking.R;
import com.smartparking.login.SessionManager;

import java.util.Map;

/**
 * Recibe el token de FCM y los mensajes push.
 *
 * El servidor manda mensajes solo de datos ("titulo", "mensaje", "tipo"), así este método
 * se ejecuta siempre (app abierta o en segundo plano) y la notificación se ve igual en ambos casos.
 */
public class SmartParkingMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    /** FCM entrega un token nuevo (primera vez, reinstalación o rotación). */
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Nuevo token FCM");
        // Sin sesión no hay a quién asignarlo; se guardará al iniciar sesión (MainActivity)
        if (SessionManager.haySesion()) {
            FcmTokenManager.guardarTokenEnServidor(token, null);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String titulo = data.get("titulo");
        String mensaje = data.get("mensaje");

        // Por si algún día se manda un mensaje tipo "notification" desde la consola de Firebase
        if (titulo == null && mensaje == null && message.getNotification() != null) {
            titulo = message.getNotification().getTitle();
            mensaje = message.getNotification().getBody();
        }
        if (titulo == null && mensaje == null) return;

        Log.d(TAG, "Mensaje recibido, tipo=" + data.get("tipo"));
        NotificationHelper.mostrar(
                getApplicationContext(),
                titulo != null ? titulo : getString(R.string.app_name),
                mensaje != null ? mensaje : "");
    }
}