package com.smartparking.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.smartparking.ui.MainActivity;
import com.smartparking.R;
import com.smartparking.network.SupabaseClient;

public class Registro extends AppCompatActivity {
    private EditText edtCorreo, edtContrasena, edtNombre, edtConfirmarContrasena, edtFolioDiscapacidad;
    private SwitchCompat switchDiscapacidad;
    private Button btnGuardar;
    private SupabaseClient supabaseClient;

    // Folio válido para verificar discapacidad
    private static final String FOLIO_VALIDO = "DISC-2025";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        edtNombre = findViewById(R.id.edtName);
        edtCorreo = findViewById(R.id.edtEmailRegister);
        edtContrasena = findViewById(R.id.edtPasswordRegister);
        edtConfirmarContrasena = findViewById(R.id.edtConfirmPassword);
        edtFolioDiscapacidad = findViewById(R.id.edtFolioDiscapacidad);
        switchDiscapacidad = findViewById(R.id.switchDiscapacidad);
        btnGuardar = findViewById(R.id.btnRegister);

        supabaseClient = new SupabaseClient();

        // Listener para mostrar/ocultar campo de folio
        switchDiscapacidad.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                edtFolioDiscapacidad.setVisibility(View.VISIBLE);
            } else {
                edtFolioDiscapacidad.setVisibility(View.GONE);
                edtFolioDiscapacidad.setText(""); // Limpiar campo al ocultar
            }
        });

        btnGuardar.setOnClickListener(view -> {
            String nombre = edtNombre.getText().toString().trim();
            String correo = edtCorreo.getText().toString().trim();
            String contrasena = edtContrasena.getText().toString().trim();
            String confirmar = edtConfirmarContrasena.getText().toString().trim();
            String folio = edtFolioDiscapacidad.getText().toString().trim();
            boolean switchActivado = switchDiscapacidad.isChecked();

            if (nombre.isEmpty() || correo.isEmpty() ||
                    contrasena.isEmpty() || confirmar.isEmpty()) {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!contrasena.equals(confirmar)) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
                return;
            }

            if (contrasena.length() < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validación de discapacidad
            boolean esDiscapacitado = false;
            if (switchActivado) {
                if (folio.isEmpty()) {
                    Toast.makeText(this, "Por favor ingresa tu folio de discapacidad", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!folio.equals(FOLIO_VALIDO)) {
                    Toast.makeText(this, "El folio de discapacidad no es válido", Toast.LENGTH_SHORT).show();
                    return;
                }

                esDiscapacitado = true;
            }

            // Crear objeto Usuario con los datos ingresados (nombre, correo y contraseña)
            Usuario usuario = new Usuario(nombre, correo, contrasena, esDiscapacitado);

            // Registro en Supabase
            supabaseClient.registrarUsuario(usuario, new SupabaseClient.SupabaseCallback() {
                @Override
                public void onSuccessUsuario() {
                    runOnUiThread(() -> {
                        Toast.makeText(Registro.this, "Registro exitoso", Toast.LENGTH_SHORT).show();

                        // Iniciar sesión automáticamente después del registro
                        supabaseClient.iniciarSesion(correo, contrasena, Registro.this, new SupabaseClient.SupabaseCallback() {
                            @Override
                            public void onSuccessUsuario() {
                                runOnUiThread(() -> {
                                    Intent intent = new Intent(Registro.this, MainActivity.class);
                                    // Estas flags limpian todo el stack de actividades anteriores
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                            }
                            @Override
                            public void onErrorUsuario(String message) {
                                runOnUiThread(() ->
                                        Toast.makeText(Registro.this, "Error al iniciar sesión: " + message, Toast.LENGTH_SHORT).show()
                                );
                            }
                        });
                    });
                }
                @Override
                public void onErrorUsuario(String message) {
                    runOnUiThread(() -> Toast.makeText(Registro.this, "Error: " + message, Toast.LENGTH_LONG).show());
                }
            });
        });
    }
}