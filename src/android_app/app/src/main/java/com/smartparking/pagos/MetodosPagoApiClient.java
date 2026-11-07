package com.smartparking.pagos;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.smartparking.network.ApiClient;
import com.stripe.android.Stripe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.smartparking.network.SupabaseClient.BASE_URL;

/**
 * Llama a la Edge Function "metodos-pago" (tarjetas guardadas en Stripe).
 * El servidor identifica al usuario por su token; la app nunca manda el customer_id.
 * Los callbacks se ejecutan en el hilo principal.
 */
public class MetodosPagoApiClient {

    private static final String TAG = "MetodosPagoApiClient";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface ListaCallback {
        void onSuccess(List<MetodoPago> metodos);
        void onError(String error);
    }

    public interface AltaCallback {
        void onSuccess(String setupIntentClientSecret, String customerId, String ephemeralKey);
        void onError(String error);
    }

    public interface LlaveClienteCallback {
        void onSuccess(String customerId, String ephemeralKey);
        void onError(String error);
    }

    public interface EliminarCallback {
        void onSuccess();
        void onError(String error);
    }

    /** Respuesta exitosa ya convertida a JSON; se procesa en el hilo de OkHttp. */
    private interface RespuestaCallback {
        void onSuccess(JSONObject json) throws Exception;
        void onError(String error);
    }

    public static void listar(ListaCallback callback) {
        enviar("listar", null, new RespuestaCallback() {
            @Override
            public void onSuccess(JSONObject json) throws Exception {
                JSONArray arreglo = json.getJSONArray("metodos");
                List<MetodoPago> metodos = new ArrayList<>();
                for (int i = 0; i < arreglo.length(); i++) {
                    JSONObject m = arreglo.getJSONObject(i);
                    metodos.add(new MetodoPago(
                            m.getString("id"),
                            m.optString("marca", ""),
                            m.optString("ultimos4", ""),
                            m.optInt("mes_exp", 0),
                            m.optInt("anio_exp", 0)));
                }
                mainHandler.post(() -> callback.onSuccess(metodos));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public static void prepararAlta(AltaCallback callback) {
        enviar("preparar_alta", null, new RespuestaCallback() {
            @Override
            public void onSuccess(JSONObject json) throws Exception {
                String secret = json.getString("setupIntentClientSecret");
                String customerId = json.getString("customerId");
                String ephemeralKey = json.getString("ephemeralKey");
                mainHandler.post(() -> callback.onSuccess(secret, customerId, ephemeralKey));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /** Llave temporal para que el PaymentSheet muestre las tarjetas guardadas al pagar. */
    public static void obtenerLlaveCliente(LlaveClienteCallback callback) {
        enviar("llave_cliente", null, new RespuestaCallback() {
            @Override
            public void onSuccess(JSONObject json) throws Exception {
                String customerId = json.getString("customerId");
                String ephemeralKey = json.getString("ephemeralKey");
                mainHandler.post(() -> callback.onSuccess(customerId, ephemeralKey));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public static void eliminar(String idMetodo, EliminarCallback callback) {
        enviar("eliminar", idMetodo, new RespuestaCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                mainHandler.post(callback::onSuccess);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private static void enviar(String accion, String idMetodo, RespuestaCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("accion", accion);
            // Versión de la API de Stripe que usa este SDK; la Ephemeral Key debe crearse con ella.
            // En la 20.1.0, ApiVersion.get() no es accesible desde Java (@JvmSynthetic);
            // Stripe.API_VERSION es público y se inicializa con ApiVersion.get().getCode().
            json.put("api_version", Stripe.API_VERSION);
            if (idMetodo != null) json.put("id_metodo", idMetodo);

            // ApiClient agrega la llave del proyecto y el token del usuario
            Request request = new Request.Builder()
                    .url(BASE_URL + "/functions/v1/metodos-pago")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

            ApiClient.get().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Error de conexión (" + accion + ")", e);
                    mainHandler.post(() -> callback.onError("Error de conexión. Revisa tu internet."));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    int codigo = response.code();
                    Log.d(TAG, accion + " -> " + codigo);

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Error del servidor: " + resp);
                        String mensaje = mensajeDeError(codigo, resp);
                        mainHandler.post(() -> callback.onError(mensaje));
                        return;
                    }

                    try {
                        callback.onSuccess(new JSONObject(resp));
                    } catch (Exception ex) {
                        Log.e(TAG, "Respuesta inválida: " + resp, ex);
                        mainHandler.post(() -> callback.onError("Error al procesar la respuesta."));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creando petición", e);
            mainHandler.post(() -> callback.onError("Error creando la petición."));
        }
    }

    private static String mensajeDeError(int codigo, String cuerpo) {
        switch (codigo) {
            case 401: return "Tu sesión expiró. Inicia sesión de nuevo.";
            case 403: return "No tienes permiso para modificar esta tarjeta.";
            case 404: return "No se encontró la tarjeta o el usuario.";
            case 500: return "Error del servidor. Intenta más tarde.";
            default:
                try {
                    String error = new JSONObject(cuerpo).optString("error", "");
                    if (!error.isEmpty()) return error;
                } catch (Exception ignored) { }
                return "Error del servidor (" + codigo + ")";
        }
    }
}
