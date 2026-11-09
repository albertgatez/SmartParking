package com.smartparking.pagos;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.network.SupabaseClient;
import com.smartparking.login.SessionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistorialActivity extends AppCompatActivity {

    private PagosAdapter adapter;
    private final List<Pago> listaPagos = new ArrayList<>();
    private final Map<String, String> nombresEstacionamiento = new HashMap<>();
    private SupabaseClient supabaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);

        RecyclerView recyclerView = findViewById(R.id.recyclerPagos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PagosAdapter(listaPagos, nombresEstacionamiento);
        recyclerView.setAdapter(adapter);

        String userId = SessionManager.getUserId();
        if (userId == null) {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        supabaseClient = new SupabaseClient();
        // Primero los nombres de estacionamiento (la tabla Pagos solo guarda el id), luego los pagos
        cargarNombresEstacionamiento(() -> cargarPagos(userId));
    }

    private void cargarNombresEstacionamiento(Runnable despues) {
        supabaseClient.obtenerEstacionamientos(new SupabaseClient.SupabaseCallbackEstacionamientos() {
            @Override
            public void onSuccess(List<Estacionamiento> estacionamientos) {
                if (isFinishing() || isDestroyed()) return;
                for (Estacionamiento e : estacionamientos) {
                    nombresEstacionamiento.put(String.valueOf(e.getId()), e.getNombre());
                }
                despues.run();
            }

            @Override
            public void onError(String message) {
                // Sin nombres se muestra el id del estacionamiento; el historial sigue funcionando
                if (isFinishing() || isDestroyed()) return;
                despues.run();
            }
        });
    }

    private void cargarPagos(String userId) {
        // El token se renueva automáticamente en ApiClient si ya expiró.
        // RLS (pagos_select_propios) ya limita los resultados a los pagos del usuario.
        supabaseClient.obtenerHistorial(userId, new SupabaseClient.SupabaseCallbackPagos() {
            @Override
            public void onSuccess(Pago[] pagos) {
                if (isFinishing() || isDestroyed()) return;
                listaPagos.clear();
                listaPagos.addAll(Arrays.asList(pagos));
                adapter.notifyDataSetChanged();

                if (pagos.length == 0) {
                    Toast.makeText(HistorialActivity.this, "No tienes pagos registrados", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(HistorialActivity.this, "Error al cargar historial: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
