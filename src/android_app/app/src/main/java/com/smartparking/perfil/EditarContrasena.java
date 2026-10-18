package com.smartparking.perfil;

import static com.smartparking.network.SupabaseClient.BASE_URL;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;



import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartparking.network.ApiClient;
import com.smartparking.R;
import com.smartparking.login.SessionManager;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.*;

public class EditarContrasena extends AppCompatActivity {

    // Vistas
    private EditText edtOldPassword, edtNewPassword, edtNewConfirmPassword;
    private Button btnGuardar;

    // Cliente
    private final OkHttpClient client = ApiClient.get();

    // Variables
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_contrasena);

        inicializarVistas();
        obtenerToken();
        configurarListeners();
    }

    private void inicializarVistas() {
        edtOldPassword = findViewById(R.id.edtOldPassword);
        edtNewPassword = findViewById(R.id.edtNewPassword);
        edtNewConfirmPassword = findViewById(R.id.edtNewConfirmPassword);
        btnGuardar = findViewById(R.id.btnGuardar);
    }

    private void obtenerToken() {
        SharedPreferences prefs = getSharedPreferences("MiSesion", MODE_PRIVATE);
        accessToken = prefs.getString("access_token", null);

        if (accessToken == null) {
            Toast.makeText(this, "Error: Sesión inválida", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void configurarListeners() {
        btnGuardar.setOnClickListener(v -> validarYCambiarContrasena());
    }

    private void validarYCambiarContrasena() {
        String oldPassword = edtOldPassword.getText().toString().trim();
        String newPassword = edtNewPassword.getText().toString().trim();
        String confirmPassword = edtNewConfirmPassword.getText().toString().trim();

        // Validaciones
        if (oldPassword.isEmpty()) {
            edtOldPassword.setError("Ingresa tu contraseña actual");
            edtOldPassword.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            edtNewPassword.setError("Ingresa una nueva contraseña");
            edtNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            edtNewPassword.setError("La contraseña debe tener al menos 6 caracteres");
            edtNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            edtNewConfirmPassword.setError("Las contraseñas no coinciden");
            edtNewConfirmPassword.requestFocus();
            return;
        }

        if (oldPassword.equals(newPassword)) {
            edtNewPassword.setError("La nueva contraseña debe ser diferente a la actual");
            edtNewPassword.requestFocus();
            return;
        }

        // Primero se verifica la contraseña actual, luego se cambia
        verificarContrasenaActual(oldPassword, newPassword);
    }

    /**
     * Comprueba la contraseña actual iniciando sesión con ella.
     * Si es correcta, guarda los tokens nuevos y cambia la contraseña.
     */
    private void verificarContrasenaActual(String oldPassword, String newPassword) {
        String email = SessionManager.getEmail();
        if (email == null) {
            Toast.makeText(this, "Error: Sesión inválida", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardar.setEnabled(false);

        JsonObject credenciales = new JsonObject();
        credenciales.addProperty("email", email);
        credenciales.addProperty("password", oldPassword);

        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/v1/token?grant_type=password")
                .post(RequestBody.create(credenciales.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    btnGuardar.setEnabled(true);
                    Toast.makeText(EditarContrasena.this, "Error de conexión", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        btnGuardar.setEnabled(true);
                        edtOldPassword.setError("La contraseña actual no es correcta");
                        edtOldPassword.requestFocus();
                    });
                    return;
                }
                try {
                    JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
                    SessionManager.guardarTokens(
                            json.get("access_token").getAsString(),
                            json.get("refresh_token").getAsString());
                } catch (Exception e) {
                    Log.w("EditarContrasena", "No se pudieron leer los tokens nuevos", e);
                }
                runOnUiThread(() -> cambiarContrasena(newPassword));
            }
        });
    }

    private void cambiarContrasena(String newPassword) {
        btnGuardar.setEnabled(false);
        Toast.makeText(this, "Cambiando contraseña...", Toast.LENGTH_SHORT).show();

        // JSON para actualizar la contraseña en Supabase Auth
        JsonObject datos = new JsonObject();
        datos.addProperty("password", newPassword);

        RequestBody body = RequestBody.create(
                datos.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        // Endpoint de Supabase Auth para actualizar usuario
        String url = BASE_URL + "/auth/v1/user";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .put(body)  // PUT para actualizar usuario
                .build();

        Log.d("EditarContrasena", "Actualizando contraseña...");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("EditarContrasena", "Error al cambiar contraseña", e);
                runOnUiThread(() -> {
                    btnGuardar.setEnabled(true);
                    Toast.makeText(EditarContrasena.this, "Error de conexión", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                Log.d("EditarContrasena", "Respuesta: " + response.code());

                runOnUiThread(() -> {
                    btnGuardar.setEnabled(true);

                    if (response.isSuccessful() || response.code() == 200) {
                        Toast.makeText(EditarContrasena.this, "¡Contraseña actualizada exitosamente!", Toast.LENGTH_LONG).show();

                        // Limpiar campos
                        edtOldPassword.setText("");
                        edtNewPassword.setText("");
                        edtNewConfirmPassword.setText("");

                        // Volver a la pantalla anterior después de 1 segundo
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(EditarContrasena.this::finish, 1000);
                    } else {
                        Toast.makeText(EditarContrasena.this, "Error al cambiar contraseña: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
