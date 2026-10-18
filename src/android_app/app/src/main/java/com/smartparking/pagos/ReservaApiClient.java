package com.smartparking.pagos;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

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
 * Llama a la Edge Function que crea la intención de pago en Stripe.
 * Los callbacks se ejecutan en el hilo principal.
 *
 * NOTA (fase 2): el monto todavía se calcula en la app; cuando la Edge Function lo
 * calcule a partir de la reserva, solo habrá que dejar de mandar "monto".
 */
public class ReservaApiClient {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ClientSecretCallback {
        void onSuccess(String clientSecret, String customerId);
        void onError(String error);
    }

    public static void obtenerClientSecret(double monto, long idReserva, String idUsuario,
                                           long idEstacionamiento, ClientSecretCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("monto", monto);
            json.put("id_reserva", idReserva);
            json.put("id_usuario", idUsuario);
            json.put("id_estacionamiento", idEstacionamiento);

            // ApiClient agrega la llave del proyecto y el token del usuario
            Request request = new Request.Builder()
                    .url(BASE_URL + "/functions/v1/crear-pago-reserva")
                    .post(RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8")))
                    .build();

            ApiClient.get().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("ReservaApiClient", "Error de conexión", e);
                    mainHandler.post(() -> callback.onError("Error de conexión"));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    Log.d("ReservaApiClient", "Response code: " + response.code());

                    if (!response.isSuccessful()) {
                        Log.e("ReservaApiClient", "Error del servidor: " + resp);
                        mainHandler.post(() -> callback.onError("Error del servidor (" + response.code() + ")"));
                        return;
                    }

                    try {
                        JSONObject jsonResp = new JSONObject(resp);
                        String clientSecret = jsonResp.getString("clientSecret");
                        String customerId = jsonResp.optString("customerId", null);
                        mainHandler.post(() -> callback.onSuccess(clientSecret, customerId));
                    } catch (Exception ex) {
                        mainHandler.post(() -> callback.onError("Error al procesar respuesta."));
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Error creando petición: " + e.getMessage());
        }
    }
}
