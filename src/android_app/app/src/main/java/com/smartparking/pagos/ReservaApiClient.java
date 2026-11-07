package com.smartparking.pagos;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.smartparking.network.SupabaseClient.BASE_URL;

import com.smartparking.network.ApiClient;

/**
 * Llama a la Edge Function crear-pago-reserva-v2 (issues 10.1 y 12.2).
 * La app manda QUÉ quiere pagar (reserva, tipo, horas extra y cupón); el monto, la comisión
 * y el descuento los calcula el servidor con los datos de la BD. El usuario se toma del token.
 * Los callbacks se ejecutan en el hilo principal.
 */
public class ReservaApiClient {

    private static final String TAG = "ReservaApiClient";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String ENDPOINT = "/functions/v1/crear-pago-reserva-v2";

    public static final String TIPO_RESERVA = "reserva";
    public static final String TIPO_EXTENSION = "extension";

    public interface ClientSecretCallback {
        void onSuccess(String clientSecret, String customerId, DesglosePago desglose);
        void onError(String error);
    }

    public interface CotizacionCallback {
        void onSuccess(DesglosePago desglose);
        void onError(String error);
    }

    /**
     * Pide al servidor el desglose sin crear el pago. Sirve para mostrar los precios
     * y para validar un cupón (si no es válido, onError recibe "Cupón inválido").
     */
    public static void cotizar(long idReserva, String tipo, int horasExtra, @Nullable String cupon,
                               CotizacionCallback callback) {
        enviar(idReserva, tipo, horasExtra, cupon, true, new RespuestaCallback() {
            @Override
            public void onSuccess(JSONObject json) throws Exception {
                DesglosePago desglose = DesglosePago.desdeJson(json.getJSONObject("desglose"));
                mainHandler.post(() -> callback.onSuccess(desglose));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /** Crea el PaymentIntent con el monto calculado por el servidor. */
    public static void obtenerClientSecret(long idReserva, String tipo, int horasExtra, @Nullable String cupon,
                                           ClientSecretCallback callback) {
        enviar(idReserva, tipo, horasExtra, cupon, false, new RespuestaCallback() {
            @Override
            public void onSuccess(JSONObject json) throws Exception {
                String clientSecret = json.getString("clientSecret");
                String customerId = json.optString("customerId", null);
                DesglosePago desglose = DesglosePago.desdeJson(json.getJSONObject("desglose"));
                mainHandler.post(() -> callback.onSuccess(clientSecret, customerId, desglose));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /** Respuesta exitosa ya convertida a JSON; se procesa en el hilo de OkHttp. */
    private interface RespuestaCallback {
        void onSuccess(JSONObject json) throws Exception;
        void onError(String error);
    }

    private static void enviar(long idReserva, String tipo, int horasExtra, @Nullable String cupon,
                               boolean soloCotizar, RespuestaCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("id_reserva", idReserva);
            json.put("tipo", tipo);
            if (TIPO_EXTENSION.equals(tipo)) json.put("horas_extra", horasExtra);
            if (cupon != null && !cupon.trim().isEmpty()) json.put("cupon", cupon.trim());
            if (soloCotizar) json.put("solo_cotizar", true);

            // ApiClient agrega la llave del proyecto y el token del usuario
            Request request = new Request.Builder()
                    .url(BASE_URL + ENDPOINT)
                    .post(RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8")))
                    .build();

            ApiClient.get().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Error de conexión", e);
                    mainHandler.post(() -> callback.onError("Error de conexión"));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Response code: " + response.code());

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Error del servidor: " + resp);
                        String mensaje = mensajeDeError(response.code(), resp);
                        mainHandler.post(() -> callback.onError(mensaje));
                        return;
                    }

                    try {
                        callback.onSuccess(new JSONObject(resp));
                    } catch (Exception ex) {
                        Log.e(TAG, "Respuesta inválida: " + resp, ex);
                        mainHandler.post(() -> callback.onError("Error al procesar respuesta."));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creando petición", e);
            mainHandler.post(() -> callback.onError("Error creando la petición."));
        }
    }

    /**
     * El servidor responde { error } con mensajes en español (por ejemplo "Cupón inválido",
     * "La reserva expiró" o "El cajón está ocupado en ese horario"); se muestran tal cual.
     */
    private static String mensajeDeError(int codigo, String cuerpo) {
        try {
            String error = new JSONObject(cuerpo).optString("error", "");
            if (!error.isEmpty()) return error;
        } catch (Exception ignored) { }
        if (codigo == 401) return "Tu sesión expiró. Inicia sesión de nuevo.";
        return "Error del servidor (" + codigo + ")";
    }
}
