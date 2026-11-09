
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

    // Callback simple para ocultar / volver a mostrar un aviso.
    public interface AccionCallback {
        void onSuccess();
        void onError(String mensaje);
    }

    /**
     * Obtiene las notificaciones del usuario que no ocultó.
     * Supabase aplica las políticas RLS mediante ApiClient.
     */
    public static void obtenerNotificaciones(
            String userId,
            ListaCallback callback) {
        obtenerNotificaciones(userId, true, callback);
    }

    /**
     * @param soloVisibles filtra oculta = false. Si la columna todavía no existe en la BD
     *                     (migración 20261109_notificaciones_ocultar sin aplicar), Supabase
     *                     responde 400 y se repite la consulta sin el filtro.
     */
    private static void obtenerNotificaciones(
            String userId,
            boolean soloVisibles,
            ListaCallback callback) {

        HttpUrl base = HttpUrl.parse(BASE_URL + "/rest/v1/Notificacion");

        if (base == null) {
            callback.onError("URL inválida");
            return;
        }

        HttpUrl.Builder builder = base.newBuilder()
                .addQueryParameter("id_usuario", "eq." + userId)
                .addQueryParameter(
                        "select",
                        "id,id_reserva,tipo,titulo,mensaje,leida,created_at"
                )
                .addQueryParameter("order", "created_at.desc");
        if (soloVisibles) {
            builder.addQueryParameter("oculta", "eq.false");
        }
        HttpUrl url = builder.build();

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

                    if (r.code() == 400 && soloVisibles && cuerpo.contains("oculta")) {
                        Log.w(TAG, "La columna oculta no existe todavía; se cargan todos los avisos");
                        obtenerNotificaciones(userId, false, callback);
                        return;
                    }

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
     * Oculta (o vuelve a mostrar) un aviso del usuario. No se borra de la BD para que los
     * recordatorios y avisos del sensor no se vuelvan a enviar (revisan los avisos existentes).
     * La política RLS solo deja cambiar la columna "oculta" de los avisos propios.
     */
    public static void cambiarOculta(long idNotificacion, boolean oculta, AccionCallback callback) {
        HttpUrl base = HttpUrl.parse(BASE_URL + "/rest/v1/Notificacion");
        if (base == null) {
            callback.onError("URL inválida");
            return;
        }

        HttpUrl url = base.newBuilder()
                .addQueryParameter("id", "eq." + idNotificacion)
                .addQueryParameter("select", "id")
                .build();

        // return=representation: si RLS no deja modificarla, regresa [] en lugar de la fila
        Request request = new Request.Builder()
                .url(url)
                .patch(RequestBody.create("{\"oculta\":" + oculta + "}", JSON))
                .header("Prefer", "return=representation")
                .build();

        ApiClient.get().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al ocultar el aviso", e);
                mainHandler.post(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    String cuerpo = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "Error HTTP " + r.code() + " al ocultar: " + cuerpo);
                        mainHandler.post(() -> callback.onError("No se pudo quitar el aviso (" + r.code() + ")"));
                        return;
                    }
                    if (cuerpo.trim().equals("[]")) {
                        Log.e(TAG, "El aviso no se modificó (¿falta la migración o no es del usuario?)");
                        mainHandler.post(() -> callback.onError("No se pudo quitar el aviso"));
                        return;
                    }
                    mainHandler.post(callback::onSuccess);
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
