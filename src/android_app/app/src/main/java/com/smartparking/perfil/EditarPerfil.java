package com.smartparking.perfil;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import static com.smartparking.network.SupabaseClient.BASE_URL;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartparking.network.ApiClient;
import com.smartparking.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.*;

public class EditarPerfil extends AppCompatActivity {
    private static final int REQUEST_IMAGE_PICK = 1;
    private static final String BUCKET = "fotos_perfil";

    // Vistas
    private ImageView imgPerfil;
    private TextView txtNombre;
    private Button btnCambiarDatos;
    private Button btnCambiarContra;
    private ProgressBar progressBar;

    // Cliente y utilidades
    private final OkHttpClient client = ApiClient.get();
    private final Gson gson = new Gson();

    // Variables
    private Uri imagenUriSeleccionada;
    private String idUsuario;
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil);

        inicializarVistas();
        obtenerDatos();
        cargarDatosUsuario();
        cargarImagenActual();
        configurarListeners();
    }

    private void inicializarVistas() {
        imgPerfil = findViewById(R.id.imgPerfil);
        txtNombre = findViewById(R.id.txtNombre);
        btnCambiarDatos = findViewById(R.id.btnCambiarDatos);
        btnCambiarContra = findViewById(R.id.btnCambiarContra);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
    }

    private void obtenerDatos() {
        // Obtener id del Intent
        idUsuario = getIntent().getStringExtra("id");

        // Obtener tokens del SharedPreferences
        SharedPreferences prefs = getSharedPreferences("MiSesion", MODE_PRIVATE);
        accessToken = prefs.getString("access_token", null);

        if (idUsuario == null || accessToken == null) {
            Toast.makeText(this, "Error: Datos de sesión inválidos", Toast.LENGTH_SHORT).show();
            Log.e("EditarPerfil", "id o accessToken es null");
            finish();
            return;
        }

        Log.d("EditarPerfil", "ID Usuario: " + idUsuario);
    }

    private void cargarDatosUsuario() {
        SharedPreferences prefs = getSharedPreferences("MiSesion", MODE_PRIVATE);
        String nombre = prefs.getString("nombre", "Usuario");
        txtNombre.setText("Buen día, " + nombre);
    }

    private void cargarImagenActual() {
        Log.d("EditarPerfil", "Cargando imagen actual para usuario: " + idUsuario);

        String url = BASE_URL + "/rest/v1/Usuario?id=eq." + idUsuario + "&select=url_foto_perfil";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("EditarPerfil", "Error al cargar imagen", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        imgPerfil.setImageResource(R.drawable.blank_profile_picture);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body() != null ? response.body().string() : "";
                Log.d("EditarPerfil", "Imagen actual consultada: " + response.code());

                if (response.isSuccessful()) {
                    try {
                        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            if (jsonArray.size() > 0) {
                                JsonObject obj = jsonArray.get(0).getAsJsonObject();

                                // Verificar si la columna existe y no es null
                                if (obj.has("url_foto_perfil") && !obj.get("url_foto_perfil").isJsonNull()) {
                                    String imagenUrl = obj.get("url_foto_perfil").getAsString();
                                    Log.d("EditarPerfil", "URL imagen encontrada: " + imagenUrl);

                                    // Cargar imagen con Glide
                                    Glide.with(EditarPerfil.this)
                                            .load(imagenUrl)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .skipMemoryCache(true)
                                            .placeholder(R.drawable.blank_profile_picture)
                                            .error(R.drawable.blank_profile_picture)
                                            .into(imgPerfil);
                                } else {
                                    Log.d("EditarPerfil", "No hay imagen, usando predeterminada");
                                    imgPerfil.setImageResource(R.drawable.blank_profile_picture);
                                }
                            } else {
                                Log.d("EditarPerfil", "Array vacío");
                                imgPerfil.setImageResource(R.drawable.blank_profile_picture);
                            }
                        });
                    } catch (Exception e) {
                        Log.e("EditarPerfil", "Error parseando JSON", e);
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                imgPerfil.setImageResource(R.drawable.blank_profile_picture);
                            }
                        });
                    }
                } else {
                    Log.e("EditarPerfil", "Error en respuesta: " + response.code());
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            imgPerfil.setImageResource(R.drawable.blank_profile_picture);
                        }
                    });
                }
            }
        });
    }

    private void configurarListeners() {
        // Presionar en la imagen para cambiar foto
        imgPerfil.setOnClickListener(v -> seleccionarImagen());

        // Botón cambiar datos
        btnCambiarDatos.setOnClickListener(v -> {
            Intent intent = new Intent(EditarPerfil.this, EditarDatosPersonales.class);
            intent.putExtra("id", idUsuario);
            startActivityForResult(intent, 100); // Usar startActivityForResult
        });

        // Botón cambiar contraseña
        btnCambiarContra.setOnClickListener(v -> {
            Intent intent = new Intent(EditarPerfil.this, EditarContrasena.class);
            startActivity(intent);
        });
    }

    private void seleccionarImagen() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Si viene de seleccionar imagen
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            imagenUriSeleccionada = data.getData();
            Log.d("EditarPerfil", "Imagen seleccionada: " + imagenUriSeleccionada);

            // Mostrar preview
            Glide.with(this)
                    .load(imagenUriSeleccionada)
                    .placeholder(R.drawable.blank_profile_picture)
                    .error(R.drawable.blank_profile_picture)
                    .into(imgPerfil);

            // Subir directamente
            subirImagen();
        }

        // Si viene de editar datos personales
        if (requestCode == 100 && resultCode == RESULT_OK) {
            Log.d("EditarPerfil", "Datos actualizados, recargando...");
            cargarDatosUsuario(); // Recargar el nombre
        }
    }

    private void subirImagen() {
        Toast.makeText(this, "Subiendo imagen...", Toast.LENGTH_SHORT).show();

        try {
            // Leer archivo
            InputStream inputStream = getContentResolver().openInputStream(imagenUriSeleccionada);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int bytesRead;
            byte[] data = new byte[4096];
            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            inputStream.close();

            byte[] bytes = buffer.toByteArray();
            Log.d("EditarPerfil", "Tamaño imagen: " + bytes.length + " bytes");

            // Crear nombre archivo
            String nombreArchivo = idUsuario + ".jpg";

            // Subir a Supabase Storage
            subirAlStorage(bytes, nombreArchivo);

        } catch (IOException e) {
            Toast.makeText(this, "Error leyendo imagen", Toast.LENGTH_SHORT).show();
            Log.e("EditarPerfil", "Error leyendo imagen", e);
        }
    }

    private void subirAlStorage(byte[] bytes, String nombreArchivo) {
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse("image/jpeg"));

        String url = BASE_URL + "/storage/v1/object/" + BUCKET + "/" + nombreArchivo;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .post(fileBody)
                .build();

        Log.d("EditarPerfil", "Subiendo a: " + url);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("EditarPerfil", "Error al subir imagen", e);
                runOnUiThread(() ->
                        Toast.makeText(EditarPerfil.this, "Error al subir imagen", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                Log.d("EditarPerfil", "Respuesta Storage: " + response.code() + " - " + resp);

                if (response.isSuccessful() || response.code() == 201) {
                    String imagenUrl = BASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + idUsuario + ".jpg";
                    Log.d("EditarPerfil", "URL generada: " + imagenUrl);

                    // Guardar URL en BD
                    guardarUrlEnBD(imagenUrl);
                } else {
                    Log.e("EditarPerfil", "Error al subir: " + response.code());
                    runOnUiThread(() ->
                            Toast.makeText(EditarPerfil.this, "Error al subir: " + response.code(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    private void guardarUrlEnBD(String imagenUrl) {
        Log.d("EditarPerfil", "Guardando URL en BD para usuario: " + idUsuario);

        JsonObject datos = new JsonObject();
        datos.addProperty("url_foto_perfil", imagenUrl);

        RequestBody body = RequestBody.create(
                datos.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        String url = BASE_URL + "/rest/v1/Usuario?id=eq." + idUsuario;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("EditarPerfil", "Error guardando URL en BD", e);
                runOnUiThread(() ->
                        Toast.makeText(EditarPerfil.this, "Error al guardar en BD", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                Log.d("EditarPerfil", "Respuesta BD: " + response.code() + " - " + resp);

                if (response.isSuccessful() || response.code() == 204) {
                    runOnUiThread(() -> {
                        Toast.makeText(EditarPerfil.this, "¡Foto actualizada correctamente!", Toast.LENGTH_SHORT).show();
                        cargarImagenActual();

                        // Enviar resultado para actualizar en ProfileFragment
                        setResult(RESULT_OK);
                    });
                } else {
                    Log.e("EditarPerfil", "Error BD: " + response.code());
                    runOnUiThread(() ->
                            Toast.makeText(EditarPerfil.this, "Error BD: " + response.code(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }
}
