package com.smartparking.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartparking.network.ApiClient;
import com.smartparking.login.InicioSesion;
import com.smartparking.R;
import com.smartparking.reservas.ReservaRepository;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.*;

import java.io.IOException;

import static com.smartparking.network.SupabaseClient.BASE_URL;
public class ProfileViewModel extends ViewModel {

    private final OkHttpClient client = ApiClient.get();
    private final Gson gson = new Gson();

    private final MutableLiveData<String> nombreUsuario = new MutableLiveData<>();
    private final MutableLiveData<String> correoUsuario = new MutableLiveData<>();

    public ProfileViewModel() {
        nombreUsuario.setValue("Usuario");
        correoUsuario.setValue("");
    }

    // Obtener nombre del storage
    public String obtenerNombreDelStorage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MiSesion", Context.MODE_PRIVATE);
        return prefs.getString("nombre", "Usuario");
    }

    // Obtener id del storage
    public String obtenerIdDelStorage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MiSesion", Context.MODE_PRIVATE);
        return prefs.getString("id", null);
    }

    // Cargar imagen de perfil desde Supabase
    public void cargarImagenPerfil(Context context, String idUsuario, ImageView imageView) {
        if (context == null || imageView == null) return;

        SharedPreferences prefs = context.getSharedPreferences("MiSesion", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access_token", null);

        if (accessToken == null || idUsuario == null) {
            imageView.setImageResource(R.drawable.blank_profile_picture);
            return;
        }

        Log.d("ProfileViewModel", "Cargando imagen para usuario: " + idUsuario);

        String url = BASE_URL + "/rest/v1/Usuario?id=eq." + idUsuario + "&select=url_foto_perfil";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("ProfileViewModel", "Error al cargar imagen", e);
                // Mostrar imagen predeterminada en caso de error
                if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                    ((androidx.appcompat.app.AppCompatActivity) context).runOnUiThread(() ->
                            imageView.setImageResource(R.drawable.blank_profile_picture)
                    );
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d("ProfileViewModel", "Imagen de perfil consultada");

                    try {
                        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

                        if (jsonArray.size() > 0) {
                            JsonObject obj = jsonArray.get(0).getAsJsonObject();

                            // Verificar si existe y no es null
                            if (obj.has("url_foto_perfil") && !obj.get("url_foto_perfil").isJsonNull()) {
                                String imagenUrl = obj.get("url_foto_perfil").getAsString();
                                Log.d("ProfileViewModel", "URL imagen: " + imagenUrl);

                                // Cargar con Glide en Main thread
                                if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                                    androidx.appcompat.app.AppCompatActivity activity = (androidx.appcompat.app.AppCompatActivity) context;
                                    activity.runOnUiThread(() ->
                                            Glide.with(context)
                                                    .load(imagenUrl)
                                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                    .skipMemoryCache(true)
                                                    .placeholder(R.drawable.blank_profile_picture)
                                                    .error(R.drawable.blank_profile_picture)
                                                    .into(imageView)
                                    );
                                }
                            } else {
                                Log.d("ProfileViewModel", "No hay imagen, usando predeterminada");
                                if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                                    ((androidx.appcompat.app.AppCompatActivity) context).runOnUiThread(() ->
                                            imageView.setImageResource(R.drawable.blank_profile_picture)
                                    );
                                }
                            }
                        } else {
                            Log.d("ProfileViewModel", "No se encontró usuario");
                            if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                                ((androidx.appcompat.app.AppCompatActivity) context).runOnUiThread(() ->
                                        imageView.setImageResource(R.drawable.blank_profile_picture)
                                );
                            }
                        }
                    } catch (Exception e) {
                        Log.e("ProfileViewModel", "Error parseando JSON", e);
                        if (context instanceof androidx.appcompat.app.AppCompatActivity) {
                            ((androidx.appcompat.app.AppCompatActivity) context).runOnUiThread(() ->
                                    imageView.setImageResource(R.drawable.blank_profile_picture)
                            );
                        }
                    }
                }
            }
        });
    }

    // Cerrar sesión
    public void cerrarSesion(Context context) {
        // Limpiar caché de Glide
        try {
            Glide.get(context).clearMemory();
            new Thread(() -> Glide.get(context).clearDiskCache()).start();
        } catch (Exception e) {
            Log.e("ProfileViewModel", "Error limpiando caché", e);
        }

        // Limpiar SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("MiSesion", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        ReservaRepository.getInstance(context).limpiarReserva(false);

        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show();

        // Ir a InicioSesion
        Intent intent = new Intent(context, InicioSesion.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        // Terminar actividades si el context es una Activity
        if (context instanceof androidx.appcompat.app.AppCompatActivity) {
            ((androidx.appcompat.app.AppCompatActivity) context).finishAffinity();
        }
    }
}