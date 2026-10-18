package com.smartparking.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smartparking.BuildConfig;
import com.smartparking.utils.DateTimeUtils;
import com.smartparking.estacionamientos.Cajon;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.login.Usuario;
import com.smartparking.pagos.Pago;
import com.smartparking.reservas.Reserva;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Acceso a Supabase (Auth + API REST).
 *
 * Todas las peticiones usan {@link ApiClient}, que agrega la llave del proyecto y el token
 * del usuario que inició sesión. Todos los callbacks se ejecutan en el hilo principal.
 */
public class SupabaseClient {
    private static final String TAG = "SupabaseClient";

    public static final String BASE_URL = "https://kjbmwwbzdsvunzndeusf.supabase.co";
    public static final String API_KEY = BuildConfig.SUPABASE_API_KEY;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = ApiClient.get();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==================== INTERFACES ====================

    public interface SupabaseCallback {
        void onSuccessUsuario();
        void onErrorUsuario(String message);
    }

    public interface SupabaseCallbackEstacionamientos {
        void onSuccess(List<Estacionamiento> estacionamientos);
        void onError(String message);
    }

    public interface SupabaseImagenesCallback {
        void onSuccess(List<String> imagenesUrls);
        void onError(String message);
    }

    public interface SupabaseCallbackCajonesReservas {
        void onSuccess(List<Cajon> cajones, List<Reserva> reservas, String urlImagenCajones);
        void onError(String message);
    }

    public interface SupabaseCallbackCrearReserva {
        void onSuccess(int reservaId);
        void onError(String message);
    }

    public interface SupabaseCallbackReservaActiva {
        void onSuccess(Reserva reserva); // null si no hay reserva activa
        void onError(String message);
    }

    public interface SupabaseCallbackDisponibilidad {
        void onResultado(boolean disponible);
        void onError(String message);
    }

    public interface SupabaseCallbackPagos {
        void onSuccess(Pago[] pagos);
        void onError(String error);
    }

    // ==================== UTILIDADES ====================

    private void enMain(Runnable r) {
        mainHandler.post(r);
    }

    private static String texto(Response response) throws IOException {
        return response.body() != null ? response.body().string() : "";
    }

    // Lectura segura de campos JSON (evita errores si un campo viene null o no existe)
    private static boolean tiene(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull();
    }

    private static String str(JsonObject o, String k) {
        return tiene(o, k) ? o.get(k).getAsString() : null;
    }

    private static int entero(JsonObject o, String k) {
        return tiene(o, k) ? o.get(k).getAsInt() : 0;
    }

    private static double decimal(JsonObject o, String k) {
        return tiene(o, k) ? o.get(k).getAsDouble() : 0.0;
    }

    private static boolean bool(JsonObject o, String k) {
        return tiene(o, k) && o.get(k).getAsBoolean();
    }

    private HttpUrl.Builder rest(String tabla) {
        HttpUrl base = HttpUrl.parse(BASE_URL + "/rest/v1/" + tabla);
        if (base == null) throw new IllegalStateException("URL inválida");
        return base.newBuilder();
    }

    private Reserva parsearReserva(JsonObject obj) {
        Reserva reserva = new Reserva();
        reserva.setId(entero(obj, "id"));
        reserva.setIdUsuario(str(obj, "id_usuario"));
        reserva.setIdCajon(entero(obj, "id_cajon"));
        reserva.setTiempoInicio(str(obj, "tiempo_inicio"));
        reserva.setTiempoFin(str(obj, "tiempo_fin"));
        reserva.setConfirmada(bool(obj, "confirmada"));
        reserva.setTokenEstacionamiento(str(obj, "token_estacionamiento"));
        return reserva;
    }

    // ==================== REGISTRO ====================

    public void registrarUsuario(Usuario usuario, SupabaseCallback callback) {
        JsonObject credenciales = new JsonObject();
        credenciales.addProperty("email", usuario.getCorreo());
        credenciales.addProperty("password", usuario.getContrasena());

        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/v1/signup")
                .post(RequestBody.create(credenciales.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al registrar usuario", e);
                enMain(() -> callback.onErrorUsuario("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                Log.d(TAG, "Código respuesta signup: " + response.code());

                if (response.isSuccessful()) {
                    try {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        JsonObject userObj = json != null && json.has("user") && json.get("user").isJsonObject()
                                ? json.getAsJsonObject("user") : null;
                        String accessToken = json != null ? str(json, "access_token") : null;

                        if (userObj != null && accessToken != null) {
                            insertarDatosUsuario(str(userObj, "id"), accessToken, usuario, callback);
                        } else {
                            // Pasa cuando Supabase exige confirmar el correo antes de iniciar sesión
                            enMain(() -> callback.onErrorUsuario("Revisa tu correo para confirmar la cuenta antes de iniciar sesión"));
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Error procesando respuesta de registro", ex);
                        enMain(() -> callback.onErrorUsuario("Error procesando la respuesta"));
                    }
                } else {
                    String mensaje = "Error al registrar usuario";
                    try {
                        JsonObject error = gson.fromJson(body, JsonObject.class);
                        String msg = error != null ? (tiene(error, "msg") ? str(error, "msg") : str(error, "error_description")) : null;
                        if (msg != null && msg.toLowerCase().contains("already registered")) {
                            mensaje = "Este correo ya está registrado";
                        } else if (msg != null) {
                            mensaje = msg;
                        }
                    } catch (Exception ignored) { }
                    String finalMensaje = mensaje;
                    enMain(() -> callback.onErrorUsuario(finalMensaje));
                }
            }
        });
    }

    private void insertarDatosUsuario(String userId, String accessToken, Usuario usuario, SupabaseCallback callback) {
        JsonObject data = new JsonObject();
        data.addProperty("id", userId);
        data.addProperty("nombre", usuario.getNombre());
        data.addProperty("correo", usuario.getCorreo());
        data.addProperty("discapacitado", usuario.isDiscapacitado());

        // Aquí todavía no hay sesión guardada, por eso se manda el token del registro explícitamente
        Request request = new Request.Builder()
                .url(BASE_URL + "/rest/v1/Usuario")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "return=minimal")
                .post(RequestBody.create(data.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al insertar usuario en tabla", e);
                enMain(() -> callback.onErrorUsuario("Error al guardar datos"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (response.isSuccessful()) {
                    enMain(callback::onSuccessUsuario);
                } else {
                    Log.e(TAG, "Error al insertar usuario: " + response.code() + " " + body);
                    enMain(() -> callback.onErrorUsuario("Error al guardar en base de datos"));
                }
            }
        });
    }

    // ==================== INICIO DE SESIÓN ====================

    public void iniciarSesion(String correo, String contrasena, Context context, SupabaseCallback callback) {
        JsonObject credenciales = new JsonObject();
        credenciales.addProperty("email", correo);
        credenciales.addProperty("password", contrasena);

        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/v1/token?grant_type=password")
                .post(RequestBody.create(credenciales.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                enMain(() -> callback.onErrorUsuario("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);

                if (!response.isSuccessful()) {
                    enMain(() -> callback.onErrorUsuario("Usuario o contraseña incorrectos"));
                    return;
                }

                try {
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    String accessToken = str(json, "access_token");
                    String refreshToken = str(json, "refresh_token");
                    JsonObject user = json.getAsJsonObject("user");
                    String userId = str(user, "id");
                    String email = str(user, "email");

                    String nombre = null;
                    String fotoPerfil = null;
                    if (tiene(user, "user_metadata")) {
                        JsonObject metadata = user.getAsJsonObject("user_metadata");
                        if (tiene(metadata, "nombre")) nombre = str(metadata, "nombre");
                        if (tiene(metadata, "name")) nombre = str(metadata, "name");
                        if (tiene(metadata, "avatar_url")) fotoPerfil = str(metadata, "avatar_url");
                        if (tiene(metadata, "picture")) fotoPerfil = str(metadata, "picture");
                    }

                    guardarSesion(context, userId, nombre, email, accessToken, refreshToken, fotoPerfil);

                    // Completa nombre y foto desde la tabla Usuario; el callback se llama una sola vez, al terminar
                    obtenerDatosComplementarios(userId, context, nombre, email, accessToken, refreshToken, fotoPerfil, callback);

                } catch (Exception e) {
                    Log.e(TAG, "Error procesando la respuesta de login", e);
                    enMain(() -> callback.onErrorUsuario("Error procesando la respuesta"));
                }
            }
        });
    }

    private void obtenerDatosComplementarios(String userId, Context context, String nombreAuth, String email,
                                             String accessToken, String refreshToken, String fotoAuth,
                                             SupabaseCallback callback) {
        Request request = new Request.Builder()
                .url(rest("Usuario").addQueryParameter("id", "eq." + userId)
                        .addQueryParameter("select", "nombre,url_foto_perfil").build())
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error obteniendo datos del usuario", e);
                enMain(callback::onSuccessUsuario); // la sesión ya está guardada
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                String nombre = nombreAuth;
                String foto = fotoAuth;

                if (response.isSuccessful()) {
                    try {
                        JsonArray usuarios = gson.fromJson(body, JsonArray.class);
                        if (usuarios != null && usuarios.size() > 0) {
                            JsonObject u = usuarios.get(0).getAsJsonObject();
                            if (tiene(u, "nombre")) nombre = str(u, "nombre");
                            if (tiene(u, "url_foto_perfil")) foto = str(u, "url_foto_perfil");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parseando datos del usuario", e);
                    }
                }

                guardarSesion(context, userId, nombre, email, accessToken, refreshToken, foto);
                enMain(callback::onSuccessUsuario);
            }
        });
    }

    private void guardarSesion(Context context, String userId, String nombre, String email,
                               String accessToken, String refreshToken, String fotoPerfil) {
        SharedPreferences prefs = context.getSharedPreferences("MiSesion", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .putString("id", userId)
                .putString("email", email)
                .putString("nombre", nombre)
                .putString("foto_perfil", fotoPerfil)
                .apply();
    }

    // ==================== ESTACIONAMIENTOS ====================

    public void obtenerEstacionamientos(SupabaseCallbackEstacionamientos callback) {
        Request request = new Request.Builder()
                .url(rest("Estacionamiento").addQueryParameter("select", "id,nombre,precio_hora,latitud,longitud").build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al obtener estacionamientos", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error " + response.code() + ": " + body);
                    enMain(() -> callback.onError("Error al obtener estacionamientos: " + response.code()));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    List<Estacionamiento> lista = new ArrayList<>();
                    if (array != null) {
                        for (JsonElement el : array) {
                            JsonObject obj = el.getAsJsonObject();
                            Estacionamiento e = new Estacionamiento();
                            e.setId(entero(obj, "id"));
                            e.setNombre(str(obj, "nombre"));
                            e.setPrecioHora(decimal(obj, "precio_hora"));
                            e.setLatitud(decimal(obj, "latitud"));
                            e.setLongitud(decimal(obj, "longitud"));
                            lista.add(e);
                        }
                    }
                    enMain(() -> callback.onSuccess(lista));
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando estacionamientos", e);
                    enMain(() -> callback.onError("Error al procesar datos"));
                }
            }
        });
    }

    /**
     * Obtiene los cajones de un estacionamiento, las reservas de HOY que los ocupan
     * y la URL de la imagen de distribución de cajones.
     */
    public void obtenerCajonesYReservas(int estacionamientoId, SupabaseCallbackCajonesReservas callback) {
        Request request = new Request.Builder()
                .url(rest("Cajon").addQueryParameter("id_estacionamiento", "eq." + estacionamientoId)
                        .addQueryParameter("select", "*")
                        .addQueryParameter("order", "seccion,numero_cajon").build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al obtener cajones", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error " + response.code() + ": " + body);
                    enMain(() -> callback.onError("Error al obtener cajones: " + response.code()));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    List<Cajon> cajones = new ArrayList<>();
                    if (array != null) {
                        for (JsonElement el : array) {
                            JsonObject obj = el.getAsJsonObject();
                            Cajon cajon = new Cajon();
                            cajon.setId(entero(obj, "id"));
                            cajon.setNumeroCajon(str(obj, "numero_cajon"));
                            cajon.setDiscapacitado(bool(obj, "discapacitado"));
                            cajon.setSeccion(str(obj, "seccion"));
                            cajon.setIdEstacionamiento(entero(obj, "id_estacionamiento"));
                            cajon.setEstadoCajon(str(obj, "estado_cajon"));
                            cajones.add(cajon);
                        }
                    }
                    obtenerReservasDeHoy(estacionamientoId, cajones, callback);
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando cajones", e);
                    enMain(() -> callback.onError("Error al procesar cajones"));
                }
            }
        });
    }

    /** Reservas (pendientes o confirmadas) de los cajones indicados que se cruzan con el día de hoy. */
    private void obtenerReservasDeHoy(int estacionamientoId, List<Cajon> cajones, SupabaseCallbackCajonesReservas callback) {
        if (cajones.isEmpty()) {
            obtenerUrlImagenCajones(estacionamientoId, cajones, new ArrayList<>(), callback);
            return;
        }

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < cajones.size(); i++) {
            if (i > 0) ids.append(',');
            ids.append(cajones.get(i).getId());
        }

        Request request = new Request.Builder()
                .url(rest("Reserva")
                        .addQueryParameter("id_cajon", "in.(" + ids + ")")
                        .addQueryParameter("tiempo_inicio", "lt." + DateTimeUtils.finDelDiaUTC())
                        .addQueryParameter("tiempo_fin", "gt." + DateTimeUtils.inicioDelDiaUTC())
                        .addQueryParameter("select", "id,id_usuario,id_cajon,tiempo_inicio,tiempo_fin,confirmada")
                        .build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al obtener reservas", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error " + response.code() + ": " + body);
                    enMain(() -> callback.onError("Error al obtener reservas: " + response.code()));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    List<Reserva> reservas = new ArrayList<>();
                    if (array != null) {
                        for (JsonElement el : array) {
                            reservas.add(parsearReserva(el.getAsJsonObject()));
                        }
                    }
                    obtenerUrlImagenCajones(estacionamientoId, cajones, reservas, callback);
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando reservas", e);
                    enMain(() -> callback.onError("Error al procesar reservas"));
                }
            }
        });
    }

    private void obtenerUrlImagenCajones(int estacionamientoId, List<Cajon> cajones, List<Reserva> reservas,
                                         SupabaseCallbackCajonesReservas callback) {
        Request request = new Request.Builder()
                .url(rest("Estacionamiento").addQueryParameter("id", "eq." + estacionamientoId)
                        .addQueryParameter("select", "url_imagen_cajones").build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al obtener URL imagen", e);
                enMain(() -> callback.onSuccess(cajones, reservas, null));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                String urlImagen = null;
                if (response.isSuccessful()) {
                    try {
                        JsonArray array = gson.fromJson(body, JsonArray.class);
                        if (array != null && array.size() > 0) {
                            urlImagen = str(array.get(0).getAsJsonObject(), "url_imagen_cajones");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parseando URL imagen", e);
                    }
                }
                String finalUrl = urlImagen;
                enMain(() -> callback.onSuccess(cajones, reservas, finalUrl));
            }
        });
    }

    public void obtenerImagenesEstacionamiento(int estacionamientoId, SupabaseImagenesCallback callback) {
        Request request = new Request.Builder()
                .url(rest("Imagen_estacionamiento").addQueryParameter("id_estacionamiento", "eq." + estacionamientoId)
                        .addQueryParameter("select", "url").build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al obtener imágenes", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    enMain(() -> callback.onError("Error al obtener imágenes: " + response.code()));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    List<String> urls = new ArrayList<>();
                    if (array != null) {
                        for (JsonElement el : array) {
                            String url = str(el.getAsJsonObject(), "url");
                            if (url != null) urls.add(url);
                        }
                    }
                    enMain(() -> callback.onSuccess(urls));
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando imágenes", e);
                    enMain(() -> callback.onError("Error al procesar imágenes"));
                }
            }
        });
    }

    // ==================== USUARIO ====================

    public interface SupabaseCallbackBooleano {
        void onResultado(boolean valor);
    }

    /** Indica si el usuario tiene acceso a cajones para personas con discapacidad. Ante error devuelve false. */
    public void obtenerDiscapacidadUsuario(String idUsuario, SupabaseCallbackBooleano callback) {
        Request request = new Request.Builder()
                .url(rest("Usuario").addQueryParameter("id", "eq." + idUsuario)
                        .addQueryParameter("select", "discapacitado").build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al obtener estado de discapacidad", e);
                enMain(() -> callback.onResultado(false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                boolean valor = false;
                if (response.isSuccessful()) {
                    try {
                        JsonArray array = gson.fromJson(body, JsonArray.class);
                        if (array != null && array.size() > 0) {
                            valor = bool(array.get(0).getAsJsonObject(), "discapacitado");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parseando discapacidad", e);
                    }
                }
                boolean resultado = valor;
                enMain(() -> callback.onResultado(resultado));
            }
        });
    }

    // ==================== RESERVAS ====================

    /**
     * Revisa que no exista otra reserva (pendiente o confirmada) del mismo cajón que se cruce
     * con el intervalo [inicioUTC, finUTC). Se usa antes de reservar y de extender.
     *
     * @param excluirReservaId id de la reserva propia que no debe contarse (-1 si no aplica)
     */
    public void verificarDisponibilidad(int idCajon, String inicioUTC, String finUTC, int excluirReservaId,
                                        SupabaseCallbackDisponibilidad callback) {
        HttpUrl.Builder url = rest("Reserva")
                .addQueryParameter("id_cajon", "eq." + idCajon)
                .addQueryParameter("tiempo_inicio", "lt." + finUTC)
                .addQueryParameter("tiempo_fin", "gt." + inicioUTC)
                .addQueryParameter("select", "id");
        if (excluirReservaId > 0) {
            url.addQueryParameter("id", "neq." + excluirReservaId);
        }

        Request request = new Request.Builder().url(url.build()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error verificando disponibilidad " + response.code() + ": " + body);
                    enMain(() -> callback.onError("No se pudo verificar la disponibilidad"));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    boolean disponible = array == null || array.size() == 0;
                    enMain(() -> callback.onResultado(disponible));
                } catch (Exception e) {
                    enMain(() -> callback.onError("No se pudo verificar la disponibilidad"));
                }
            }
        });
    }

    /** Crea una nueva reserva pendiente (confirmada = false). */
    public void crearReserva(String idUsuario, int idCajon, String tiempoInicio, String tiempoFin,
                             SupabaseCallbackCrearReserva callback) {
        JsonObject json = new JsonObject();
        json.addProperty("id_usuario", idUsuario);
        json.addProperty("id_cajon", idCajon);
        json.addProperty("tiempo_inicio", tiempoInicio);
        json.addProperty("tiempo_fin", tiempoFin);
        json.addProperty("confirmada", false);

        Request request = new Request.Builder()
                .url(BASE_URL + "/rest/v1/Reserva")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(json.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al crear reserva", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error " + response.code() + ": " + body);
                    // 409 = conflicto (p. ej. la restricción anti-traslape de la BD)
                    String msg = response.code() == 409
                            ? "Ese horario acaba de ser reservado por otra persona"
                            : "No se pudo crear la reserva (" + response.code() + ")";
                    enMain(() -> callback.onError(msg));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    if (array != null && array.size() > 0) {
                        int reservaId = entero(array.get(0).getAsJsonObject(), "id");
                        enMain(() -> callback.onSuccess(reservaId));
                    } else {
                        enMain(() -> callback.onError("No se pudo obtener el ID de la reserva"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando respuesta", e);
                    enMain(() -> callback.onError("Error al procesar respuesta"));
                }
            }
        });
    }

    /**
     * Busca la reserva del usuario que todavía no termina (tiempo_fin en el futuro),
     * con los datos del cajón y del estacionamiento.
     */
    public void verificarReservaActiva(String idUsuario, SupabaseCallbackReservaActiva callback) {
        Request request = new Request.Builder()
                .url(rest("Reserva")
                        .addQueryParameter("id_usuario", "eq." + idUsuario)
                        .addQueryParameter("tiempo_fin", "gte." + DateTimeUtils.ahoraUTC())
                        .addQueryParameter("select", "*,Cajon(*,Estacionamiento(nombre,precio_hora))")
                        .addQueryParameter("order", "tiempo_inicio.asc")
                        .build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al verificar reserva activa", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error " + response.code() + ": " + body);
                    enMain(() -> callback.onError("Error al verificar reserva: " + response.code()));
                    return;
                }
                try {
                    JsonArray array = gson.fromJson(body, JsonArray.class);
                    if (array == null || array.size() == 0) {
                        enMain(() -> callback.onSuccess(null));
                        return;
                    }

                    JsonObject obj = array.get(0).getAsJsonObject();
                    Reserva reserva = parsearReserva(obj);

                    if (tiene(obj, "Cajon") && obj.get("Cajon").isJsonObject()) {
                        JsonObject cajon = obj.getAsJsonObject("Cajon");
                        reserva.setNumeroCajon(str(cajon, "numero_cajon"));
                        reserva.setSeccion(str(cajon, "seccion"));
                        reserva.setDiscapacitado(bool(cajon, "discapacitado"));
                        reserva.setIdEstacionamiento(entero(cajon, "id_estacionamiento"));

                        if (tiene(cajon, "Estacionamiento") && cajon.get("Estacionamiento").isJsonObject()) {
                            JsonObject est = cajon.getAsJsonObject("Estacionamiento");
                            reserva.setNombreEstacionamiento(str(est, "nombre"));
                            reserva.setPrecioHora(decimal(est, "precio_hora"));
                        }
                    }
                    enMain(() -> callback.onSuccess(reserva));
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando reserva activa", e);
                    enMain(() -> callback.onError("Error al procesar reserva"));
                }
            }
        });
    }

    /**
     * Confirma una reserva pendiente (confirmada = true).
     * NOTA: en la fase 2 esto lo hará el webhook de Stripe en el servidor, no la app.
     */
    public void confirmarReserva(int reservaId, SupabaseCallback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("confirmada", true);
        patchReserva(reservaId, json, "confirmar", callback);
    }

    /** Elimina una reserva (cuando expira el temporizador de pago). */
    public void eliminarReserva(int reservaId, SupabaseCallback callback) {
        Request request = new Request.Builder()
                .url(rest("Reserva").addQueryParameter("id", "eq." + reservaId).build())
                .delete()
                .build();
        ejecutarSimple(request, "eliminar reserva", callback);
    }

    /** Guarda el token del boleto escaneado (QR) en la reserva. */
    public void actualizarTokenEstacionamiento(int reservaId, String token, SupabaseCallback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("token_estacionamiento", token);
        patchReserva(reservaId, json, "actualizar token", callback);
    }

    /** Extiende el tiempo de una reserva actualizando tiempo_fin. */
    public void extenderReserva(int reservaId, String nuevoTiempoFin, SupabaseCallback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("tiempo_fin", nuevoTiempoFin);
        patchReserva(reservaId, json, "extender", callback);
    }

    private void patchReserva(int reservaId, JsonObject cambios, String accion, SupabaseCallback callback) {
        Request request = new Request.Builder()
                .url(rest("Reserva").addQueryParameter("id", "eq." + reservaId).build())
                .patch(RequestBody.create(cambios.toString(), JSON))
                .build();
        ejecutarSimple(request, accion, callback);
    }

    private void ejecutarSimple(Request request, String accion, SupabaseCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error al " + accion, e);
                enMain(() -> callback.onErrorUsuario("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (response.isSuccessful()) {
                    enMain(callback::onSuccessUsuario);
                } else {
                    Log.e(TAG, "Error al " + accion + ": " + response.code() + " " + body);
                    enMain(() -> callback.onErrorUsuario("Error al " + accion + ": " + response.code()));
                }
            }
        });
    }

    // ==================== HISTORIAL DE PAGOS ====================

    public void obtenerHistorial(String userId, SupabaseCallbackPagos callback) {
        Request request = new Request.Builder()
                .url(rest("Pagos").addQueryParameter("usuario_id", "eq." + userId)
                        .addQueryParameter("select", "*")
                        .addQueryParameter("order", "fecha_pago.desc").build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error de conexión al obtener historial", e);
                enMain(() -> callback.onError("Error de conexión"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = texto(response);
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error historial " + response.code() + ": " + body);
                    enMain(() -> callback.onError("Error HTTP " + response.code()));
                    return;
                }
                try {
                    Pago[] pagos = gson.fromJson(body, Pago[].class);
                    Pago[] resultado = pagos != null ? pagos : new Pago[0];
                    enMain(() -> callback.onSuccess(resultado));
                } catch (Exception e) {
                    Log.e(TAG, "Error al parsear historial", e);
                    enMain(() -> callback.onError("Error al procesar datos"));
                }
            }
        });
    }
}
