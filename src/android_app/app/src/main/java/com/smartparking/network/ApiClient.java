package com.smartparking.network;

import com.smartparking.login.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Cliente HTTP único para toda la app.
 *
 * - Agrega siempre el header "apikey".
 * - Si la petición no trae "Authorization", agrega el token del usuario que inició sesión
 *   (y lo renueva antes si está por expirar). Así Supabase aplica las políticas RLS
 *   con la identidad del usuario y no con la llave anónima.
 * - Si el servidor responde 401 por token vencido, lo renueva y reintenta una vez.
 */
public final class ApiClient {

    private static OkHttpClient instancia;

    private ApiClient() {}

    public static synchronized OkHttpClient get() {
        if (instancia == null) {
            instancia = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder builder = original.newBuilder()
                                .header("apikey", SupabaseClient.API_KEY);

                        if (original.header("Authorization") == null && !esEndpointPublicoDeAuth(original)) {
                            String token = SessionManager.getAccessToken();
                            if (token != null && SessionManager.tokenPorExpirar(token)) {
                                String renovado = SessionManager.renovarToken(token);
                                if (renovado != null) token = renovado;
                            }
                            builder.header("Authorization", "Bearer " + (token != null ? token : SupabaseClient.API_KEY));
                        }
                        return chain.proceed(builder.build());
                    })
                    .authenticator((route, response) -> {
                        Request peticion = response.request();
                        String auth = peticion.header("Authorization");
                        String tokenSesion = SessionManager.getAccessToken();

                        // Solo se reintenta una vez y solo si se usó el token de la sesión
                        if (response.priorResponse() != null || auth == null || tokenSesion == null) return null;
                        String tokenUsado = auth.replace("Bearer ", "");
                        if (tokenUsado.equals(SupabaseClient.API_KEY)) return null;

                        String nuevo = SessionManager.renovarToken(tokenUsado);
                        if (nuevo == null) return null;
                        return peticion.newBuilder().header("Authorization", "Bearer " + nuevo).build();
                    })
                    .build();
        }
        return instancia;
    }

    /** Endpoints de Auth que no deben llevar el token del usuario (login, registro, renovación). */
    private static boolean esEndpointPublicoDeAuth(Request request) {
        String path = request.url().encodedPath();
        return path.startsWith("/auth/v1/token") || path.startsWith("/auth/v1/signup")
                || path.startsWith("/auth/v1/recover");
    }
}
