package com.smartparking.reservas;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.smartparking.utils.DateTimeUtils;
import com.smartparking.ui.MainActivity;
import com.smartparking.R;
import com.smartparking.network.SupabaseClient;
import com.smartparking.estacionamientos.Cajon;
import com.smartparking.estacionamientos.CajonConHorarios;
import com.smartparking.estacionamientos.CajonConHorarios.EstadoHorario;
import com.smartparking.estacionamientos.CajonConHorarios.HorarioEstado;
import com.smartparking.estacionamientos.CajonesAdapter;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.login.InicioSesion;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SeleccionReservaActivity extends AppCompatActivity {

    private static final String TAG = "SeleccionReserva";
    private static final String EXTRA_ESTACIONAMIENTO_ID = "estacionamiento_id";
    private static final String EXTRA_ESTACIONAMIENTO_NOMBRE = "estacionamiento_nombre";
    private static final String EXTRA_PRECIO_HORA = "precio_hora";
    private static final long TIEMPO_EXPIRACION_MS = 5 * 60 * 1000;

    private TextView tvFecha, tvErrorMessage;
    private ImageView ivDistribucionCajones;
    private RecyclerView recyclerViewCajones;
    private Button btnContinuar;
    private ProgressBar progressBar;

    private CajonesAdapter cajonesAdapter;
    private SupabaseClient supabaseClient;
    private ReservaRepository reservaRepo;

    private int estacionamientoId;
    private String estacionamientoNombre;
    private double precioHora;
    private List<CajonConHorarios> cajonesConHorarios = new ArrayList<>();

    // Variable para saber si el usuario es discapacitado
    private boolean usuarioEsDiscapacitado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccion_reserva);

        Log.d(TAG, "onCreate - SeleccionReservaActivity");

        estacionamientoId = getIntent().getIntExtra(EXTRA_ESTACIONAMIENTO_ID, -1);
        estacionamientoNombre = getIntent().getStringExtra(EXTRA_ESTACIONAMIENTO_NOMBRE);
        precioHora = getIntent().getDoubleExtra(EXTRA_PRECIO_HORA, 0.0);

        Log.d(TAG, "Estacionamiento recibido: ID=" + estacionamientoId + ", Nombre=" + estacionamientoNombre);

        if (estacionamientoId == -1) {
            Toast.makeText(this, "Error: No se recibió el estacionamiento", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();

        supabaseClient = new SupabaseClient();
        try {
            reservaRepo = ReservaRepository.getInstance(this);
            Log.d(TAG, "Repository obtenido");
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener ViewModel", e);
        }

        setupFechaActual();
        setupRecyclerView();

        // Primero obtener estado de discapacidad del usuario, luego cargar cajones
        obtenerEstadoDiscapacidadUsuario();

        btnContinuar.setOnClickListener(v -> crearReservaPendiente());
    }

    private void initViews() {
        tvFecha = findViewById(R.id.tvFecha);
        ivDistribucionCajones = findViewById(R.id.ivDistribucionCajones);
        recyclerViewCajones = findViewById(R.id.recyclerViewCajones);
        btnContinuar = findViewById(R.id.btnContinuar);
        progressBar = findViewById(R.id.progressBar);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        btnContinuar.setEnabled(false);
        btnContinuar.setAlpha(0.5f);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Selecciona tu lugar");
        }
    }

    private void setupFechaActual() {
        SimpleDateFormat sdf = new SimpleDateFormat("'Hoy' dd MMM", new Locale("es", "MX"));
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("America/Mexico_City"));
        String fechaFormateada = sdf.format(new Date());
        tvFecha.setText("< " + fechaFormateada + " >");
    }

    private void setupRecyclerView() {
        recyclerViewCajones.setLayoutManager(new LinearLayoutManager(this));
        cajonesAdapter = new CajonesAdapter(cajonesConHorarios, (cajonPos, horarioPos, seleccionado) -> {
            actualizarEstadoBotonContinuar();
        });
        recyclerViewCajones.setAdapter(cajonesAdapter);
    }

    /**
     * Obtiene el estado de discapacidad del usuario desde la base de datos
     */
    private void obtenerEstadoDiscapacidadUsuario() {
        mostrarCargando(true);

        String userId = getUserIdFromSharedPreferences();
        if (userId == null) return;

        supabaseClient.obtenerDiscapacidadUsuario(userId, esDiscapacitado -> {
            if (isFinishing() || isDestroyed()) return;
            usuarioEsDiscapacitado = esDiscapacitado;
            Log.d(TAG, "Usuario es discapacitado: " + usuarioEsDiscapacitado);
            cargarCajonesYReservas();
        });
    }

    private void cargarCajonesYReservas() {
        mostrarCargando(true);

        supabaseClient.obtenerCajonesYReservas(estacionamientoId, new SupabaseClient.SupabaseCallbackCajonesReservas() {
            @Override
            public void onSuccess(List<Cajon> cajones, List<Reserva> reservas, String urlImagenCajones) {
                mostrarCargando(false);

                if (urlImagenCajones != null && !urlImagenCajones.isEmpty()) {
                    Glide.with(SeleccionReservaActivity.this)
                            .load(urlImagenCajones)
                            .placeholder(R.drawable.ic_placeholder_image)
                            .into(ivDistribucionCajones);
                }

                procesarCajonesYReservas(cajones, reservas);
            }

            @Override
            public void onError(String message) {
                mostrarCargando(false);
                mostrarError(message);
            }
        });
    }

    private void procesarCajonesYReservas(List<Cajon> cajones, List<Reserva> reservas) {
        cajonesConHorarios.clear();

        int horaActual = DateTimeUtils.horaActualLocal();

        for (Cajon cajon : cajones) {
            // Verificar si el usuario puede seleccionar este cajón
            boolean cajonAccesible = usuarioEsDiscapacitado || !cajon.isDiscapacitado();

            List<HorarioEstado> horarios = new ArrayList<>();

            for (int hora = 0; hora < 24; hora++) {
                EstadoHorario estado;

                if (hora < horaActual) {
                    estado = EstadoHorario.PASADO;
                } else if (!cajonAccesible) {
                    // Si el usuario no puede acceder a este cajón, marcarlo como no disponible
                    estado = EstadoHorario.RESTRINGIDO;
                } else if (estaReservado(cajon.getId(), hora, reservas)) {
                    estado = EstadoHorario.OCUPADO;
                } else {
                    estado = EstadoHorario.DISPONIBLE;
                }

                horarios.add(new HorarioEstado(hora, estado));
            }

            CajonConHorarios cajonConHorarios = new CajonConHorarios(cajon, horarios);
            cajonConHorarios.setAccesibleParaUsuario(cajonAccesible);
            this.cajonesConHorarios.add(cajonConHorarios);
        }

        cajonesAdapter.notifyDataSetChanged();

        Log.d(TAG, "Cajones procesados: " + cajones.size() +
                ", Usuario discapacitado: " + usuarioEsDiscapacitado);
    }

    /**
     * Un horario (hora:00 a hora+1:00 de hoy) está ocupado si alguna reserva del cajón se cruza con él.
     * Se comparan fechas completas, así las reservas de otros días o que terminan a medianoche
     * se evalúan correctamente.
     */
    private boolean estaReservado(int cajonId, int hora, List<Reserva> reservas) {
        long inicioSlot = DateTimeUtils.fechaHoyALaHora(hora, 0).getTime();
        long finSlot = DateTimeUtils.fechaHoyALaHora(hora + 1, 0).getTime();

        for (Reserva reserva : reservas) {
            if (reserva.getIdCajon() != cajonId) continue;
            try {
                long inicio = DateTimeUtils.parsearUTCALocal(reserva.getTiempoInicio()).getTime();
                long fin = DateTimeUtils.parsearUTCALocal(reserva.getTiempoFin()).getTime();
                if (inicio < finSlot && fin > inicioSlot) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error al parsear fechas de reserva", e);
            }
        }
        return false;
    }

    private void actualizarEstadoBotonContinuar() {
        boolean haySeleccion = false;

        for (CajonConHorarios cajonConHorarios : cajonesConHorarios) {
            for (HorarioEstado horario : cajonConHorarios.getHorarios()) {
                if (horario.isSeleccionado()) {
                    haySeleccion = true;
                    break;
                }
            }
            if (haySeleccion) break;
        }

        btnContinuar.setEnabled(haySeleccion);
        btnContinuar.setAlpha(haySeleccion ? 1.0f : 0.5f);
    }

    private void crearReservaPendiente() {
        CajonConHorarios cajonSeleccionado = null;
        List<Integer> horasSeleccionadas = new ArrayList<>();

        for (CajonConHorarios cajonConHorarios : cajonesConHorarios) {
            List<Integer> horas = new ArrayList<>();
            for (HorarioEstado horario : cajonConHorarios.getHorarios()) {
                if (horario.isSeleccionado()) {
                    horas.add(horario.getHora());
                }
            }

            if (!horas.isEmpty()) {
                cajonSeleccionado = cajonConHorarios;
                horasSeleccionadas = horas;
                break;
            }
        }

        if (cajonSeleccionado == null || horasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Por favor selecciona al menos un horario", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validación adicional: verificar que el cajón sea accesible
        if (!cajonSeleccionado.isAccesibleParaUsuario()) {
            Toast.makeText(this, "No puedes reservar este cajón", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!sonHorasConsecutivas(horasSeleccionadas)) {
            Toast.makeText(this, "Por favor selecciona horas consecutivas", Toast.LENGTH_SHORT).show();
            return;
        }

        int horaInicio = horasSeleccionadas.get(0);
        int horaFin = horasSeleccionadas.get(horasSeleccionadas.size() - 1) + 1;

        String tiempoInicio = DateTimeUtils.convertirLocalAUTC(horaInicio, 0);
        String tiempoFin = DateTimeUtils.convertirLocalAUTC(horaFin, 0);

        String idUsuario = getUserIdFromSharedPreferences();

        if (idUsuario == null || idUsuario.isEmpty()) {
            Toast.makeText(this, "Error: No hay usuario logueado", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Creando reserva para usuario: " + idUsuario);

        mostrarCargando(true);

        CajonConHorarios finalCajonSeleccionado = cajonSeleccionado;
        int idCajon = cajonSeleccionado.getCajon().getId();

        // Se vuelve a revisar justo antes de reservar, por si otra persona lo apartó mientras tanto
        supabaseClient.verificarDisponibilidad(idCajon, tiempoInicio, tiempoFin, -1,
                new SupabaseClient.SupabaseCallbackDisponibilidad() {
                    @Override
                    public void onResultado(boolean disponible) {
                        if (isFinishing() || isDestroyed()) return;
                        if (!disponible) {
                            mostrarCargando(false);
                            Toast.makeText(SeleccionReservaActivity.this,
                                    "Ese horario acaba de ser reservado. Elige otro.", Toast.LENGTH_LONG).show();
                            cargarCajonesYReservas();
                            return;
                        }
                        insertarReserva(idUsuario, finalCajonSeleccionado, tiempoInicio, tiempoFin);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        mostrarCargando(false);
                        Toast.makeText(SeleccionReservaActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void insertarReserva(String idUsuario, CajonConHorarios finalCajonSeleccionado,
                                 String tiempoInicio, String tiempoFin) {
        supabaseClient.crearReserva(idUsuario, finalCajonSeleccionado.getCajon().getId(),
                tiempoInicio, tiempoFin,
                new SupabaseClient.SupabaseCallbackCrearReserva() {
                    @Override
                    public void onSuccess(int reservaId) {
                        if (isFinishing() || isDestroyed()) return;
                        mostrarCargando(false);

                        Log.d(TAG, "Reserva creada exitosamente con ID: " + reservaId);

                        Reserva reserva = new Reserva();
                        reserva.setId(reservaId);
                        reserva.setIdUsuario(idUsuario);
                        reserva.setIdCajon(finalCajonSeleccionado.getCajon().getId());
                        reserva.setTiempoInicio(tiempoInicio);
                        reserva.setTiempoFin(tiempoFin);
                        reserva.setConfirmada(false);
                        reserva.setNumeroCajon(finalCajonSeleccionado.getCajon().getNumeroCajon());
                        reserva.setSeccion(finalCajonSeleccionado.getCajon().getSeccion());
                        reserva.setDiscapacitado(finalCajonSeleccionado.getCajon().isDiscapacitado());
                        reserva.setNombreEstacionamiento(estacionamientoNombre);
                        reserva.setPrecioHora(precioHora);
                        reserva.setIdEstacionamiento(estacionamientoId);

                        long expiracion = System.currentTimeMillis() + TIEMPO_EXPIRACION_MS;
                        reserva.setExpiracionMs(expiracion);

                        reservaRepo.guardarReserva(reserva);

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                SeleccionReservaActivity.this::navegarAlCarrito, 300);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        mostrarCargando(false);
                        Log.e(TAG, "Error al crear reserva: " + message);
                        Toast.makeText(SeleccionReservaActivity.this,
                                "Error al crear reserva: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean sonHorasConsecutivas(List<Integer> horas) {
        if (horas.size() <= 1) return true;
        java.util.Collections.sort(horas);
        for (int i = 1; i < horas.size(); i++) {
            if (horas.get(i) - horas.get(i - 1) != 1) {
                return false;
            }
        }
        return true;
    }

    private void navegarAlCarrito() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_to", "shoppingcart");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void mostrarCargando(boolean mostrar) {
        progressBar.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        recyclerViewCajones.setVisibility(mostrar ? View.GONE : View.VISIBLE);
        btnContinuar.setEnabled(!mostrar);
    }

    private void mostrarError(String mensaje) {
        tvErrorMessage.setText(mensaje);
        tvErrorMessage.setVisibility(View.VISIBLE);
        recyclerViewCajones.setVisibility(View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static Intent createIntent(android.content.Context context, Estacionamiento estacionamiento) {
        Intent intent = new Intent(context, SeleccionReservaActivity.class);
        intent.putExtra(EXTRA_ESTACIONAMIENTO_ID, estacionamiento.getId());
        intent.putExtra(EXTRA_ESTACIONAMIENTO_NOMBRE, estacionamiento.getNombre());
        intent.putExtra(EXTRA_PRECIO_HORA, estacionamiento.getPrecioHora());
        return intent;
    }

    private String getUserIdFromSharedPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("MiSesion", MODE_PRIVATE);
        String userId = sharedPreferences.getString("id", null);
        if (userId == null) {
            Toast.makeText(this, "Error: Usuario no identificado. Por favor, inicie sesión de nuevo.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, InicioSesion.class);
            startActivity(intent);
            finish();
        }
        return userId;
    }
}