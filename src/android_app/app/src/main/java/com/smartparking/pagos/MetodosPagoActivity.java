package com.smartparking.pagos;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;
import com.smartparking.login.SessionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tarjetas guardadas del usuario (issue 7.3).
 * Las tarjetas las guarda Stripe; la app solo recibe marca, últimos 4 dígitos y vencimiento.
 *
 * Dos modos:
 * - Normal (desde Perfil): ver, agregar, eliminar y elegir la predeterminada.
 * - Selección (desde el Carrito o la extensión de tiempo): además se elige con qué tarjeta pagar
 *   y se devuelve su id con RESULT_OK ({@link #EXTRA_ID_METODO}).
 *
 * Para agregar una tarjeta se abre {@link AgregarTarjetaActivity}.
 */
public class MetodosPagoActivity extends AppCompatActivity {

    public static final String EXTRA_MODO_SELECCION = "modo_seleccion";
    /** Entrada: la tarjeta elegida ahora (opcional). Salida: la tarjeta elegida. */
    public static final String EXTRA_ID_METODO = "id_metodo";
    /** Mensaje opcional que se muestra arriba en modo selección. */
    public static final String EXTRA_MENSAJE = "mensaje";

    /** Intent para abrir la pantalla en modo selección. */
    public static Intent intentSeleccion(Context context, @Nullable String idActual, @Nullable String mensaje) {
        Intent intent = new Intent(context, MetodosPagoActivity.class);
        intent.putExtra(EXTRA_MODO_SELECCION, true);
        if (idActual != null) intent.putExtra(EXTRA_ID_METODO, idActual);
        if (mensaje != null) intent.putExtra(EXTRA_MENSAJE, mensaje);
        return intent;
    }

    private final List<MetodoPago> listaMetodos = new ArrayList<>();
    private MetodosPagoAdapter adapter;

    private boolean modoSeleccion;
    private String idSeleccionado;
    /** Ids que había antes de agregar una tarjeta, para seleccionar la nueva al volver. */
    private Set<String> idsAntesDeAgregar = null;

    private RecyclerView recyclerMetodos;
    private ProgressBar progressCarga;
    private TextView txtSinTarjetas;
    private LinearLayout layoutError;
    private TextView txtError;
    private Button btnAgregarTarjeta;
    private Button btnUsarTarjeta;

    private ActivityResultLauncher<Intent> agregarTarjetaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodos_pago);

        if (SessionManager.getUserId() == null) {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        modoSeleccion = getIntent().getBooleanExtra(EXTRA_MODO_SELECCION, false);
        idSeleccionado = getIntent().getStringExtra(EXTRA_ID_METODO);

        recyclerMetodos = findViewById(R.id.recyclerMetodos);
        progressCarga = findViewById(R.id.progressCarga);
        txtSinTarjetas = findViewById(R.id.txtSinTarjetas);
        layoutError = findViewById(R.id.layoutError);
        txtError = findViewById(R.id.txtError);
        btnAgregarTarjeta = findViewById(R.id.btnAgregarTarjeta);
        btnUsarTarjeta = findViewById(R.id.btnUsarTarjeta);
        Button btnReintentar = findViewById(R.id.btnReintentar);

        if (modoSeleccion) {
            ((TextView) findViewById(R.id.txtMetodosTitulo)).setText("Elige con qué pagar");
            String mensaje = getIntent().getStringExtra(EXTRA_MENSAJE);
            TextView txtSubtitulo = findViewById(R.id.txtMetodosSubtitulo);
            txtSubtitulo.setText(mensaje != null ? mensaje
                    : "Toca una tarjeta para seleccionarla o agrega una nueva.");
            btnUsarTarjeta.setVisibility(View.VISIBLE);
        }

        recyclerMetodos.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MetodosPagoAdapter(listaMetodos, new MetodosPagoAdapter.Listener() {
            @Override
            public void onEliminar(MetodoPago metodo) {
                confirmarEliminar(metodo);
            }

            @Override
            public void onPredeterminar(MetodoPago metodo) {
                predeterminar(metodo);
            }

            @Override
            public void onSeleccionar(MetodoPago metodo) {
                seleccionar(metodo.getId());
            }
        });
        adapter.setModoSeleccion(modoSeleccion);
        recyclerMetodos.setAdapter(adapter);

        // Debe registrarse en onCreate
        agregarTarjetaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        cargarMetodos(); // selecciona la nueva si estamos en modo selección
                    } else {
                        idsAntesDeAgregar = null;
                    }
                });

        btnAgregarTarjeta.setOnClickListener(v -> agregarTarjeta());
        btnUsarTarjeta.setOnClickListener(v -> usarTarjeta());
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
                listaMetodos.clear();
                listaMetodos.addAll(metodos);

                if (modoSeleccion) {
                    resolverSeleccion();
                }
                mostrarCargando(false);
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
                actualizarBotonUsar();
            }
        });
    }

    /**
     * Decide qué tarjeta queda seleccionada: la recién agregada, la que ya estaba elegida
     * (si sigue existiendo) o la predeterminada.
     */
    private void resolverSeleccion() {
        String nueva = null;
        if (idsAntesDeAgregar != null) {
            for (MetodoPago m : listaMetodos) {
                if (!idsAntesDeAgregar.contains(m.getId())) {
                    nueva = m.getId();
                    break;
                }
            }
            idsAntesDeAgregar = null;
        }

        if (nueva != null) {
            idSeleccionado = nueva;
        } else if (MetodoPago.buscarPorId(listaMetodos, idSeleccionado) == null) {
            MetodoPago predeterminada = MetodoPago.buscarPredeterminada(listaMetodos);
            idSeleccionado = predeterminada != null ? predeterminada.getId() : null;
        }
        adapter.setIdSeleccionado(idSeleccionado);
        actualizarBotonUsar();
    }

    // ---------- Selección (modo selección) ----------

    private void seleccionar(String id) {
        idSeleccionado = id;
        adapter.setIdSeleccionado(id);
        actualizarBotonUsar();
    }

    private void usarTarjeta() {
        if (idSeleccionado == null) {
            Toast.makeText(this, "Selecciona o agrega una tarjeta", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_ID_METODO, idSeleccionado);
        setResult(RESULT_OK, data);
        finish();
    }

    private void actualizarBotonUsar() {
        if (!modoSeleccion) return;
        btnUsarTarjeta.setEnabled(idSeleccionado != null && progressCarga.getVisibility() != View.VISIBLE);
    }

    // ---------- Agregar ----------

    private void agregarTarjeta() {
        idsAntesDeAgregar = new HashSet<>();
        for (MetodoPago m : listaMetodos) idsAntesDeAgregar.add(m.getId());
        agregarTarjetaLauncher.launch(new Intent(this, AgregarTarjetaActivity.class));
    }

    // ---------- Predeterminada ----------

    /** La predeterminada es la que se selecciona sola en el Carrito y en la extensión de tiempo. */
    private void predeterminar(MetodoPago metodo) {
        mostrarCargando(true);

        MetodosPagoApiClient.predeterminar(metodo.getId(), new MetodosPagoApiClient.EliminarCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(MetodosPagoActivity.this,
                        metodo.getMarcaVisible() + " •••• " + metodo.getUltimos4() + " es tu tarjeta predeterminada",
                        Toast.LENGTH_SHORT).show();
                cargarMetodos();
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                Toast.makeText(MetodosPagoActivity.this, "No se pudo cambiar la predeterminada: " + error, Toast.LENGTH_LONG).show();
            }
        });
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
        actualizarBotonUsar();
    }
}
