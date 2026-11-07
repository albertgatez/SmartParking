package com.smartparking.pagos;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;
import com.smartparking.login.SessionManager;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Tarjetas guardadas del usuario (issue 7.3).
 * Las tarjetas las guarda Stripe; la app solo recibe marca, últimos 4 dígitos y vencimiento.
 * Para agregar una tarjeta se usa el PaymentSheet de Stripe con un SetupIntent.
 */
public class MetodosPagoActivity extends AppCompatActivity {

    private static final String TAG = "MetodosPagoActivity";

    private final List<MetodoPago> listaMetodos = new ArrayList<>();
    private MetodosPagoAdapter adapter;
    private PaymentSheet paymentSheet;

    private RecyclerView recyclerMetodos;
    private ProgressBar progressCarga;
    private TextView txtSinTarjetas;
    private LinearLayout layoutError;
    private TextView txtError;
    private Button btnAgregarTarjeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodos_pago);

        if (SessionManager.getUserId() == null) {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        recyclerMetodos = findViewById(R.id.recyclerMetodos);
        progressCarga = findViewById(R.id.progressCarga);
        txtSinTarjetas = findViewById(R.id.txtSinTarjetas);
        layoutError = findViewById(R.id.layoutError);
        txtError = findViewById(R.id.txtError);
        btnAgregarTarjeta = findViewById(R.id.btnAgregarTarjeta);
        Button btnReintentar = findViewById(R.id.btnReintentar);

        recyclerMetodos.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MetodosPagoAdapter(listaMetodos, this::confirmarEliminar);
        recyclerMetodos.setAdapter(adapter);

        // Debe registrarse en onCreate
        paymentSheet = new PaymentSheet(this, this::onResultadoAlta);

        btnAgregarTarjeta.setOnClickListener(v -> agregarTarjeta());
        btnReintentar.setOnClickListener(v -> cargarMetodos());

        cargarMetodos();
    }

    // ---------- Lista ----------

    private void cargarMetodos() {
        mostrarCargando(true);
        layoutError.setVisibility(View.GONE);
        txtSinTarjetas.setVisibility(View.GONE);

        MetodosPagoApiClient.listar(new MetodosPagoApiClient.ListaCallback() {
            @Override
            public void onSuccess(List<MetodoPago> metodos) {
                if (isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                listaMetodos.clear();
                listaMetodos.addAll(metodos);
                adapter.notifyDataSetChanged();

                boolean vacia = metodos.isEmpty();
                txtSinTarjetas.setVisibility(vacia ? View.VISIBLE : View.GONE);
                recyclerMetodos.setVisibility(vacia ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                listaMetodos.clear();
                adapter.notifyDataSetChanged();
                recyclerMetodos.setVisibility(View.GONE);
                txtError.setText("No se pudieron cargar tus tarjetas.\n" + error);
                layoutError.setVisibility(View.VISIBLE);
            }
        });
    }

    // ---------- Agregar ----------

    private void agregarTarjeta() {
        mostrarCargando(true);

        MetodosPagoApiClient.prepararAlta(new MetodosPagoApiClient.AltaCallback() {
            @Override
            public void onSuccess(String setupIntentClientSecret, String customerId, String ephemeralKey) {
                if (isFinishing() || isDestroyed()) return;

                PaymentSheet.Configuration configuration =
                        new PaymentSheet.Configuration.Builder("Smart Parking")
                                .customer(new PaymentSheet.CustomerConfiguration(customerId, ephemeralKey))
                                .allowsDelayedPaymentMethods(false)
                                .build();

                paymentSheet.presentWithSetupIntent(setupIntentClientSecret, configuration);
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                Toast.makeText(MetodosPagoActivity.this, "No se pudo agregar la tarjeta: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onResultadoAlta(PaymentSheetResult result) {
        if (result instanceof PaymentSheetResult.Completed) {
            Toast.makeText(this, "Tarjeta guardada", Toast.LENGTH_SHORT).show();
            cargarMetodos();

        } else if (result instanceof PaymentSheetResult.Canceled) {
            mostrarCargando(false);

        } else if (result instanceof PaymentSheetResult.Failed) {
            mostrarCargando(false);
            String error = ((PaymentSheetResult.Failed) result).getError().getMessage();
            Log.e(TAG, "Error en PaymentSheet: " + error);
            Toast.makeText(this, "No se pudo guardar la tarjeta: " + error, Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Eliminar ----------

    private void confirmarEliminar(MetodoPago metodo) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar tarjeta")
                .setMessage("¿Quieres eliminar la tarjeta " + metodo.getMarcaVisible()
                        + " •••• " + metodo.getUltimos4() + "?")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminar(metodo))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminar(MetodoPago metodo) {
        mostrarCargando(true);

        MetodosPagoApiClient.eliminar(metodo.getId(), new MetodosPagoApiClient.EliminarCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(MetodosPagoActivity.this, "Tarjeta eliminada", Toast.LENGTH_SHORT).show();
                cargarMetodos();
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                Toast.makeText(MetodosPagoActivity.this, "No se pudo eliminar la tarjeta: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Estado de la pantalla ----------

    private void mostrarCargando(boolean cargando) {
        progressCarga.setVisibility(cargando ? View.VISIBLE : View.GONE);
        btnAgregarTarjeta.setEnabled(!cargando);
        adapter.setHabilitado(!cargando);
    }
}
