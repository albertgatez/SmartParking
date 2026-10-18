package com.smartparking.pagos;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;
import com.smartparking.network.SupabaseClient;
import com.smartparking.login.SessionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HistorialActivity extends AppCompatActivity {

    private PagosAdapter adapter;
    private final List<Pago> listaPagos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);

        RecyclerView recyclerView = findViewById(R.id.recyclerPagos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PagosAdapter(listaPagos);
        recyclerView.setAdapter(adapter);

        String userId = SessionManager.getUserId();
        if (userId == null) {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // El token se renueva automáticamente en ApiClient si ya expiró
        new SupabaseClient().obtenerHistorial(userId, new SupabaseClient.SupabaseCallbackPagos() {
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
