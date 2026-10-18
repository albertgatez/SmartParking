package com.smartparking.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartparking.network.SupabaseClient;
import com.smartparking.app.SmartParkingApp;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Maneja la sesión del usuario (tokens de Supabase Auth) en un solo lugar.
 * Se inicializa una vez desde {@link SmartParkingApp}.
 *
 * Los datos se siguen guardando en las SharedPreferences "MiSesion" con las mismas
 * claves que ya usaba la app, para no romper las pantallas existentes.
 */
public final class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREFS = "MiSesion";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_ID = "id";
    private static final String KEY_EMAIL = "email";

    // Renovar el token si le quedan menos de 60 segundos
    private static final long MARGEN_EXPIRACION_S = 60;

    private static Context appContext;

    // Cliente sin interceptores, solo para renovar el token (evita recursión)
    private static final OkHttpClient clienteRenovacion = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private SessionManager() {}

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    private static SharedPreferences prefs() {
        if (appContext == null) {
            throw new IllegalStateException("SessionManager no inicializado. Revisa SmartParkingApp en el Manifest.");
        }
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getAccessToken() {
        return prefs().getString(KEY_ACCESS, null);
    }

    public static String getRefreshToken() {
        return prefs().getString(KEY_REFRESH, null);
    }

    public static String getUserId() {
        return prefs().getString(KEY_ID, null);
    }

    public static String getEmail() {
        return prefs().getString(KEY_EMAIL, null);
    }

    public static boolean haySesion() {
        return getAccessToken() != null;
    }

    public static void guardarTokens(String accessToken, String refreshToken) {
        prefs().edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .apply();
    }

    public static void cerrarSesion() {
        prefs().edit().clear().apply();
    }

    /** true si el token ya expiró o expira en menos de MARGEN_EXPIRACION_S. */
    public static boolean tokenPorExpirar(String token) {
        long exp = obtenerExpiracion(token);
        if (exp <= 0) return false; // si no se puede leer, se deja que el servidor decida
        long ahora = System.currentTimeMillis() / 1000;
        return exp - ahora < MARGEN_EXPIRACION_S;
    }

    private static long obtenerExpiracion(String token) {
        try {
            String[] partes = token.split("\\.");
            if (partes.length < 2) return 0;
            byte[] payload = Base64.decode(partes[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JsonObject json = JsonParser.parseString(new String(payload, "UTF-8")).getAsJsonObject();
            return json.has("exp") ? json.get("exp").getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Renueva el access token con el refresh token. Se ejecuta de forma síncrona,
     * por lo que solo debe llamarse desde un hilo secundario (OkHttp ya lo hace así).
     *
     * @param tokenFallido el token que se usó en la petición que falló; si otro hilo
     *                     ya lo renovó, se devuelve el nuevo sin volver a pedirlo.
     * @return el nuevo access token, o null si no se pudo renovar.
     */
    public static synchronized String renovarToken(String tokenFallido) {
        String actual = getAccessToken();
        if (actual != null && !actual.equals(tokenFallido) && !tokenPorExpirar(actual)) {
            return actual; // otro hilo ya lo renovó
        }

        String refresh = getRefreshToken();
        if (refresh == null) return null;

        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refresh);

        Request request = new Request.Builder()
                .url(SupabaseClient.BASE_URL + "/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SupabaseClient.API_KEY)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = clienteRenovacion.newCall(request).execute()) {
            String texto = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                Log.w(TAG, "No se pudo renovar el token: HTTP " + response.code());
                return null;
            }
            JsonObject json = JsonParser.parseString(texto).getAsJsonObject();
            String nuevoAccess = json.get("access_token").getAsString();
            String nuevoRefresh = json.get("refresh_token").getAsString();
            guardarTokens(nuevoAccess, nuevoRefresh);
            Log.d(TAG, "Token renovado");
            return nuevoAccess;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Error renovando el token", e);
            return null;
        }
    }
}
