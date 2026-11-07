package com.smartparking.notificaciones;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonObject;
import com.smartparking.login.SessionManager;
import com.smartparking.network.ApiClient;
import com.smartparking.network.SupabaseClient;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Mantiene el token de FCM del teléfono en la columna Usuario.fcm_token, para que el servidor
 * sepa a qué dispositivo mandar las notificaciones de cada usuario.
 *
 * Los callbacks NO se ejecutan en el hilo principal: no tocar la interfaz desde ahí.
 */
public final class FcmTokenManager {

    private static final String TAG = "FcmTokenManager";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface Resultado {
        void alTerminar(boolean exito);
    }

    private FcmTokenManager() {}

    /** Obtiene el token actual de FCM y lo guarda para el usuario con sesión iniciada. */
    public static void registrarTokenActual(@Nullable Resultado resultado) {
        if (!SessionManager.haySesion()) {
            notificar(resultado, false);
            return;
        }

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.w(TAG, "No se pudo obtener el token de FCM", task.getException());
                notificar(resultado, false);
                return;
            }
            guardarTokenEnServidor(task.getResult(), resultado);
        });
    }

    /** PATCH Usuario.fcm_token = token (la política RLS solo deja modificar la fila propia). */
    public static void guardarTokenEnServidor(@NonNull String token, @Nullable Resultado resultado) {
        String userId = SessionManager.getUserId();
        if (userId == null) {
            notificar(resultado, false);
            return;
        }
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("fcm_token", token);
        enviarPatch(userId, cuerpo.toString(), null, "guardar token", resultado);
    }

    /**
     * Al cerrar sesión: quita el token del usuario en el servidor y pide un token nuevo al
     * teléfono, para que quien inicie sesión después en este dispositivo no reciba las
     * notificaciones del usuario anterior. Debe llamarse ANTES de borrar la sesión local.
     */
    public static void limpiarTokenAlCerrarSesion() {
        String userId = SessionManager.getUserId();
        String accessToken = SessionManager.getAccessToken();

        if (userId != null && accessToken != null) {
            // Se manda el token de acceso explícito porque la sesión local se borra enseguida
            enviarPatch(userId, "{\"fcm_token\":null}", accessToken, "borrar token", null);
        }

        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "No se pudo borrar el token local de FCM", task.getException());
            }
        });
    }

    private static void enviarPatch(String userId, String cuerpoJson, @Nullable String accessToken,
                                    String accion, @Nullable Resultado resultado) {
        HttpUrl base = HttpUrl.parse(SupabaseClient.BASE_URL + "/rest/v1/Usuario");
        if (base == null) {
            notificar(resultado, false);
            return;
        }
        HttpUrl url = base.newBuilder().addQueryParameter("id", "eq." + userId).build();

        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("Prefer", "return=minimal")
                .patch(RequestBody.create(cuerpoJson, JSON));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        ApiClient.get().newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "Error de red al " + accion, e);
                notificar(resultado, false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.w(TAG, "Error al " + accion + ": HTTP " + r.code());
                    }
                    notificar(resultado, r.isSuccessful());
                }
            }
        });
    }

    private static void notificar(@Nullable Resultado resultado, boolean exito) {
        if (resultado != null) resultado.alTerminar(exito);
    }
}