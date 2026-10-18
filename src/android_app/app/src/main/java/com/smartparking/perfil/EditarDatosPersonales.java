package com.smartparking.perfil;

import static com.smartparking.network.SupabaseClient.BASE_URL;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartparking.network.ApiClient;
import com.smartparking.R;
import com.smartparking.login.InicioSesion;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.*;

public class EditarDatosPersonales extends AppCompatActivity {
    private static final String FOLIO_VALIDO = "DISC-2025";

    // Vistas
    private EditText edtEmail, edtNombre, edtTelefono, edtFolioDiscapacidad;
    private SwitchCompat switchDiscapacidad;
    private LinearLayout layoutDiscapacidad;
    private Button btnGuardar;
    private ProgressBar progressBar;

    // Cliente y utilidades
    private final OkHttpClient client = ApiClient.get();
    private final Gson gson = new Gson();

    // Variables
    private String idUsuario;
    private String accessToken;
    private String emailOriginal;
    private boolean esDiscapacitadoOriginal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_datos_personales);

        inicializarVistas();
        obtenerDatos();
        cargarDatosActuales();
        configurarListeners();
    }

    private void inicializarVistas() {
        edtEmail = findViewById(R.id.edtEmail);
        edtNombre = findViewById(R.id.edtNombre);
        edtTelefono = findViewById(R.id.edtTelefono);
        edtFolioDiscapacidad = findViewById(R.id.edtFolioDiscapacidad);
        switchDiscapacidad = findViewById(R.id.switchDiscapacidad);
        layoutDiscapacidad = findViewById(R.id.layoutDiscapacidad);
        btnGuardar = findViewById(R.id.btnGuardar);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
    }

    private void obtenerDatos() {
        idUsuario = getIntent().getStringExtra("id");
        SharedPreferences prefs = getSharedPreferences("MiSesion", MODE_PRIVATE);
        accessToken = prefs.getString("access_token", null);

        if (idUsuario == null || accessToken == null) {
            Toast.makeText(this, "Error: Datos de sesión inválidos", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d("EditarDatos", "ID Usuario: " + idUsuario);
    }

    private void cargarDatosActuales() {
        String url = BASE_URL + "/rest/v1/Usuario?id=eq." + idUsuario + "&select=correo,nombre,telefono,discapacitado";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("EditarDatos", "Error al cargar datos", e);
                runOnUiThread(() ->
                        Toast.makeText(EditarDatosPersonales.this, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body() != null ? response.body().string() : "";
                Log.d("EditarDatos", "Datos cargados: " + response.code());

                if (response.isSuccessful()) {
                    try {
                        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

                        if (jsonArray.size() > 0) {
                            JsonObject obj = jsonArray.get(0).getAsJsonObject();

                            runOnUiThread(() -> {
                                if (obj.has("correo") && !obj.get("correo").isJsonNull()) {
                                    emailOriginal = obj.get("correo").getAsString();
                                    edtEmail.setText(emailOriginal);
                                }
                                if (obj.has("nombre") && !obj.get("nombre").isJsonNull()) {
                                    edtNombre.setText(obj.get("nombre").getAsString());
                                }
                                if (obj.has("telefono") && !obj.get("telefono").isJsonNull()) {
                                    edtTelefono.setText(obj.get("telefono").getAsString());
                                }
                                if (obj.has("discapacitado") && !obj.get("discapacitado").isJsonNull()) {
                                    esDiscapacitadoOriginal = obj.get("discapacitado").getAsBoolean();
                                    configurarVistaDiscapacidad(esDiscapacitadoOriginal);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e("EditarDatos", "Error parseando JSON", e);
                    }
                }
            }
        });
    }

    private void configurarVistaDiscapacidad(boolean esDiscapacitado) {
        if (esDiscapacitado) {
            // Ya es discapacitado: mostrar switch activado y deshabilitado
            switchDiscapacidad.setChecked(true);
            switchDiscapacidad.setEnabled(false);
            edtFolioDiscapacidad.setVisibility(View.GONE);
        } else {
            // No es discapacitado: switch apagado y habilitado
            switchDiscapacidad.setChecked(false);
            switchDiscapacidad.setEnabled(true);
            edtFolioDiscapacidad.setVisibility(View.GONE);
        }
    }

    private void configurarListeners() {
        btnGuardar.setOnClickListener(v -> validarYGuardar());

        // Listener del switch de discapacidad
        switchDiscapacidad.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Solo mostrar folio si NO era discapacitado originalmente
            if (!esDiscapacitadoOriginal) {
                if (isChecked) {
                    edtFolioDiscapacidad.setVisibility(View.VISIBLE);
                } else {
                    edtFolioDiscapacidad.setVisibility(View.GONE);
                    edtFolioDiscapacidad.setText("");
                }
            }
        });

        // Validación en tiempo real del teléfono
        edtTelefono.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String telefono = s.toString().trim();
                if (!telefono.isEmpty()) {
                    if (!telefono.matches("\\d{10}")) {
                        edtTelefono.setError("Debe tener 10 dígitos numéricos");
                    } else {
                        edtTelefono.setError(null);
                    }
                } else {
                    edtTelefono.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void validarYGuardar() {
        String email = edtEmail.getText().toString().trim();
        String nombre = edtNombre.getText().toString().trim();
        String telefono = edtTelefono.getText().toString().trim();
        String folio = edtFolioDiscapacidad.getText().toString().trim();
        boolean switchActivado = switchDiscapacidad.isChecked();

        if (email.isEmpty()) {
            edtEmail.setError("El correo es requerido");
            edtEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Correo inválido");
            edtEmail.requestFocus();
            return;
        }

        if (nombre.isEmpty()) {
            edtNombre.setError("El nombre es requerido");
            edtNombre.requestFocus();
            return;
        }

        if (!telefono.isEmpty() && !telefono.matches("\\d{10}")) {
            edtTelefono.setError("El teléfono debe tener 10 dígitos");
            edtTelefono.requestFocus();
            return;
        }

        // Validar discapacidad solo si el usuario NO era discapacitado y activó el switch
        boolean nuevoEstadoDiscapacitado = esDiscapacitadoOriginal;
        if (!esDiscapacitadoOriginal && switchActivado) {
            if (folio.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu folio de discapacidad", Toast.LENGTH_SHORT).show();
                edtFolioDiscapacidad.requestFocus();
                return;
            }
            if (!folio.equals(FOLIO_VALIDO)) {
                Toast.makeText(this, "El folio de discapacidad no es válido", Toast.LENGTH_SHORT).show();
                edtFolioDiscapacidad.requestFocus();
                return;
            }
            nuevoEstadoDiscapacitado = true;
        }

        guardarCambios(email, nombre, telefono, nuevoEstadoDiscapacitado);
    }

    private void guardarCambios(String email, String nombre, String telefono, boolean discapacitado) {
        btnGuardar.setEnabled(false);
        Toast.makeText(this, "Guardando cambios...", Toast.LENGTH_SHORT).show();
        actualizarTablaUsuario(email, nombre, telefono, discapacitado);
    }

    private void actualizarTablaUsuario(String email, String nombre, String telefono, boolean discapacitado) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("correo", email);
            jsonObject.put("nombre", nombre);
            jsonObject.put("discapacitado", discapacitado);

            if (!telefono.isEmpty()) {
                jsonObject.put("telefono", telefono);
            }
        } catch (JSONException e) {
            Log.e("EditarDatos", "Error creando JSON", e);
            runOnUiThread(() -> {
                btnGuardar.setEnabled(true);
                Toast.makeText(EditarDatosPersonales.this, "Error al procesar los datos", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        RequestBody body = RequestBody.create(
                jsonObject.toString(),
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
                Log.e("EditarDatos", "Error al guardar en Usuario", e);
                runOnUiThread(() -> {
                    btnGuardar.setEnabled(true);
                    Toast.makeText(EditarDatosPersonales.this, "Error al guardar cambios", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                Log.d("EditarDatos", "Respuesta Usuario: " + response.code());

                if (response.isSuccessful() || response.code() == 204) {
                    if (!email.equals(emailOriginal)) {
                        Log.d("EditarDatos", "Email cambió, actualizando en Auth");
                        actualizarEmailEnAuth(email, nombre, telefono);
                    } else {
                        runOnUiThread(() -> finalizarActualizacion(nombre));
                    }
                } else {
                    runOnUiThread(() -> {
                        btnGuardar.setEnabled(true);
                        Toast.makeText(EditarDatosPersonales.this, "Error al guardar: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void actualizarEmailEnAuth(String nuevoEmail, String nombre, String telefono) {
        JsonObject datos = new JsonObject();
        datos.addProperty("email", nuevoEmail);

        RequestBody body = RequestBody.create(
                datos.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        String url = BASE_URL + "/auth/v1/user";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .put(body)
                .build();

        Log.d("EditarDatos", "Actualizando email en Auth: " + nuevoEmail);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("EditarDatos", "Error al actualizar email en Auth", e);
                runOnUiThread(() -> {
                    btnGuardar.setEnabled(true);
                    Toast.makeText(EditarDatosPersonales.this, "Datos guardados pero el email no se actualizó en Auth", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                Log.d("EditarDatos", "Respuesta Auth: " + response.code());

                runOnUiThread(() -> {
                    if (response.isSuccessful() || response.code() == 200) {
                        Log.d("EditarDatos", "Email actualizado en Auth correctamente");
                        finalizarActualizacion(nombre);
                    } else {
                        btnGuardar.setEnabled(true);
                        Toast.makeText(EditarDatosPersonales.this, "Datos guardados pero el email no se actualizó completamente", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void finalizarActualizacion(String nombre) {
        SharedPreferences prefs = getSharedPreferences("MiSesion", MODE_PRIVATE);
        prefs.edit().putString("nombre", nombre).apply();

        btnGuardar.setEnabled(true);

        if (!edtEmail.getText().toString().trim().equals(emailOriginal)) {
            new AlertDialog.Builder(EditarDatosPersonales.this)
                    .setTitle("Email actualizado")
                    .setMessage("Tu email ha sido actualizado. Por seguridad, debes iniciar sesión nuevamente con tu nuevo correo.")
                    .setPositiveButton("Iniciar sesión", (dialog, which) -> {
                        prefs.edit().clear().apply();
                        Intent intent = new Intent(EditarDatosPersonales.this, InicioSesion.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        } else {
            Toast.makeText(EditarDatosPersonales.this, "¡Cambios guardados exitosamente!", Toast.LENGTH_SHORT).show();
            Intent resultIntent = new Intent();
            resultIntent.putExtra("datos_actualizados", true);
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }
}