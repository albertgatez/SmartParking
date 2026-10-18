package com.smartparking.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.smartparking.R;
import com.smartparking.databinding.ActivityMainBinding;
import com.smartparking.login.InicioSesion;
import com.smartparking.login.SessionManager;
import com.smartparking.reservas.ReservaRepository;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ReservaRepository reservaRepo;
    private NavController navController;

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

        manejarNavegacion(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        manejarNavegacion(intent);
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
