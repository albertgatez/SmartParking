package com.smartparking.pagos;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.network.SupabaseClient;
import com.smartparking.login.SessionManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistorialActivity extends AppCompatActivity {
    private static final String TAG = "HistorialActivity";

    private PagosAdapter adapter;
    private final List<Pago> listaPagos = new ArrayList<>();
    private final Map<String, String> nombresEstacionamiento = new HashMap<>();
    private SupabaseClient supabaseClient;
    private Button btnExportar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);

        RecyclerView recyclerView = findViewById(R.id.recyclerPagos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PagosAdapter(listaPagos, nombresEstacionamiento);
        recyclerView.setAdapter(adapter);

        btnExportar = findViewById(R.id.btnExportar);
        btnExportar.setOnClickListener(v -> exportarHistorial());

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

    /** Pide los cajones de las reservas, genera el CSV y abre el menú de compartir de Android. */
    private void exportarHistorial() {
        if (listaPagos.isEmpty()) {
            Toast.makeText(this, "No hay pagos para exportar", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = SessionManager.getUserId();
        if (userId == null) return;

        btnExportar.setEnabled(false);
        supabaseClient.obtenerCajonesDeReservas(userId, new SupabaseClient.SupabaseCallbackCajones() {
            @Override
            public void onSuccess(Map<String, String> cajonPorReserva) {
                if (isFinishing() || isDestroyed()) return;
                btnExportar.setEnabled(true);
                try {
                    String csv = ExportadorPagos.generarCsv(listaPagos, nombresEstacionamiento, cajonPorReserva);
                    compartir(ExportadorPagos.escribir(HistorialActivity.this, csv));
                } catch (IOException e) {
                    Log.e(TAG, "Error al escribir el CSV", e);
                    Toast.makeText(HistorialActivity.this, "No se pudo generar el archivo", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                btnExportar.setEnabled(true);
                Toast.makeText(HistorialActivity.this, "Error al exportar: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void compartir(File archivo) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", archivo);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Historial de pagos - SmartParking");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Compartir historial"));
    }
}
