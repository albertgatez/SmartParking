
package com.smartparking.notificaciones;

import static com.smartparking.network.SupabaseClient.BASE_URL;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.smartparking.network.ApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class NotificacionesApiClient {

    private static final String TAG = "NotificacionesApi";
    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private static final Gson gson = new Gson();
    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private NotificacionesApiClient() {}

    // Callback para devolver la lista de notificaciones.
    public interface ListaCallback {
        void onSuccess(List<Notificacion> lista);
        void onError(String mensaje);
    }

    /**
     * Obtiene las notificaciones del usuario.
     * Supabase aplica las políticas RLS mediante ApiClient.
     */
    public static void obtenerNotificaciones(
            String userId,
            ListaCallback callback) {

        HttpUrl base = HttpUrl.parse(BASE_URL + "/rest/v1/Notificacion");

        if (base == null) {
            callback.onError("URL inválida");
            return;
        }

        HttpUrl url = base.newBuilder()
                .addQueryParameter("id_usuario", "eq." + userId)
                .addQueryParameter(
                        "select",
                        "id,id_reserva,tipo,titulo,mensaje,leida,created_at"
                )
                .addQueryParameter("order", "created_at.desc")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        ApiClient.get().newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e) {

                Log.e(TAG, "Error al obtener notificaciones", e);

                mainHandler.post(() ->
                        callback.onError("Error de conexión")
                );
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response) throws IOException {

                try (Response r = response) {

                    String cuerpo = r.body() != null
                            ? r.body().string()
                            : "";

                    if (!r.isSuccessful()) {
                        Log.e(TAG,
                                "Error HTTP " + r.code() + ": " + cuerpo);

                        mainHandler.post(() ->
                                callback.onError(
                                        "Error al obtener notificaciones: "
                                                + r.code()
                                )
                        );
                        return;
                    }

                    try {
                        Notificacion[] resultado =
                                gson.fromJson(cuerpo, Notificacion[].class);

                        List<Notificacion> lista = new ArrayList<>();

                        if (resultado != null) {
                            for (Notificacion notificacion : resultado) {
                                lista.add(notificacion);
                            }
                        }

                        mainHandler.post(() ->
                                callback.onSuccess(lista)
                        );

                    } catch (Exception e) {
                        Log.e(TAG,
                                "Error al procesar notificaciones", e);

                        mainHandler.post(() ->
                                callback.onError(
                                        "Error al procesar notificaciones"
                                )
                        );
                    }
                }
            }
        });
    }

    /**
     * Solicita la notificación de bienvenida mediante Edge Function.
     */
    public static void solicitarBienvenida() {

        Request request = new Request.Builder()
                .url(BASE_URL + "/functions/v1/notificar-bienvenida")
                .post(RequestBody.create("{}", JSON))
                .build();

        ApiClient.get().newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(
                    @NonNull Call call,
                    @NonNull IOException e) {

                Log.w(TAG,
                        "No se pudo solicitar la notificación de bienvenida", e);
            }

            @Override
            public void onResponse(
                    @NonNull Call call,
                    @NonNull Response response) throws IOException {

                try (Response r = response) {
                    String cuerpo = r.body() != null
                            ? r.body().string()
                            : "";

                    Log.d(TAG,
                            "notificar-bienvenida -> HTTP "
                                    + r.code() + " " + cuerpo);
                }
            }
        });
    }
}
