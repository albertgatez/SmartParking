package com.smartparking.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.smartparking.R;
import com.smartparking.databinding.ActivityMainBinding;
import com.smartparking.login.InicioSesion;
import com.smartparking.login.SessionManager;
import com.smartparking.notificaciones.FcmTokenManager;
import com.smartparking.reservas.ReservaRepository;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ReservaRepository reservaRepo;
    private NavController navController;

    // Permiso de notificaciones (Android 13+). Debe registrarse antes de que la Activity inicie.
    private final ActivityResultLauncher<String> permisoNotificaciones =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    concedido -> Log.d(TAG, "Permiso de notificaciones concedido: " + concedido));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sin sesión -> pantalla de inicio de sesión
        if (!SessionManager.haySesion()) {
            startActivity(new Intent(this, InicioSesion.class));
            finish();
            return;
        }

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        reservaRepo = ReservaRepository.getInstance(this);

        // Navegación inferior
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_discover, R.id.navigation_shoppingcart,
                R.id.navigation_notifications, R.id.navigation_profile)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Limpia reservas vencidas y recupera la reserva del servidor si existe
        reservaRepo.verificarExpiracion();
        reservaRepo.sincronizarConServidor(SessionManager.getUserId());

        // Notificaciones: permiso y token de FCM del usuario (el token puede cambiar entre sesiones)
        pedirPermisoNotificaciones();
        FcmTokenManager.registrarTokenActual(null);

        manejarNavegacion(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        manejarNavegacion(intent);
    }

    /** En Android 13+ las notificaciones necesitan permiso del usuario. */
    private void pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Permite que otras pantallas abran directamente una pestaña (ej. el carrito tras reservar). */
    private void manejarNavegacion(Intent intent) {
        if (intent == null || navController == null) return;
        String navigateTo = intent.getStringExtra("navigate_to");
        if ("shoppingcart".equals(navigateTo)) {
            Log.d(TAG, "Navegando al carrito");
            navController.navigate(R.id.navigation_shoppingcart);
            intent.removeExtra("navigate_to"); // evita volver a navegar al rotar la pantalla
        }
    }
}