package com.smartparking.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smartparking.ui.MainActivity;
import com.smartparking.R;
import com.smartparking.network.SupabaseClient;

public class InicioSesion extends AppCompatActivity {
    // Constantes públicas
    public static final String PREFS_NAME = "MiSesion";
    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_REFRESH_TOKEN = "refresh_token";
    public static final String KEY_USER_ID = "id";
    public static final String KEY_USERNAME = "nombre";
    public static final String KEY_USER_EMAIL = "email";
    public static final String KEY_USER_PHOTO = "foto_perfil";

    private EditText correoInput, contraseniaInput;
    private Button btnIniciarSesion;
    private TextView registro;
    private SupabaseClient supabaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inicio_sesion);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);

        if (token != null) {
            // Si ya hay sesión guardada, ir directamente a Main
            Intent intent = new Intent(InicioSesion.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        correoInput = findViewById(R.id.edtEmail);
        contraseniaInput = findViewById(R.id.edtPassword);
        btnIniciarSesion = findViewById(R.id.btnLogin);
        registro = findViewById(R.id.txtRegistrar);

        supabaseClient = new SupabaseClient();

        registro.setOnClickListener(v -> {
            Intent intent = new Intent(InicioSesion.this, Registro.class);
            startActivity(intent);
        });

        btnIniciarSesion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String correo = correoInput.getText().toString().trim();
                String contrasenia = contraseniaInput.getText().toString().trim();

                if (correo.isEmpty() || contrasenia.isEmpty()) {
                    Toast.makeText(InicioSesion.this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                    return;
                }

                supabaseClient.iniciarSesion(correo, contrasenia, InicioSesion.this, new SupabaseClient.SupabaseCallback() {
                    @Override
                    public void onSuccessUsuario() {
                        runOnUiThread(() -> {
                            Toast.makeText(InicioSesion.this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(InicioSesion.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                    @Override
                    public void onErrorUsuario(String message) {
                        runOnUiThread(() ->
                                Toast.makeText(InicioSesion.this, "Error: " + message, Toast.LENGTH_SHORT).show()
                        );
                    }
                });
            }
        });
    }
}
