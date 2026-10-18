package com.smartparking.ui.home;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import com.smartparking.utils.DateTimeUtils;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.R;
import com.smartparking.reservas.Reserva;
import com.smartparking.pagos.ReservaApiClient;
import com.smartparking.reservas.ReservaRepository;
import com.smartparking.reservas.SeleccionReservaActivity;
import com.smartparking.network.SupabaseClient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment de inicio que muestra el estado de la reserva del usuario
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final double COMISION_EXTENSION = 0.05; // 5% para extensiones

    // Stripe
    private PaymentSheet paymentSheet;
    private String clientSecret;
    private double montoExtension = 0.0;

    // Vistas - Estado de reserva
    private TextView tvEstadoReserva;
    private CardView cvEscanearBoleto;
    private CardView cvEstadoReserva;
    private RecyclerView rvEstacionamientos;
    private TextView tvEstacionamientosDisponibles;

    // Vistas - Detalles de reserva activa
    private View layoutDetallesReserva;
    private TextView tvNombreEstacionamiento;
    private TextView tvSeccionCajon;
    private TextView tvHorarioReserva;
    private TextView tvPrecioHora;

    // Vistas - Extensión de tiempo
    private View layoutExtension;
    private TextView tvHorasExtension;
    private ImageButton btnMenos, btnMas;
    private Button btnAceptarExtension, btnReiniciar;

    // Dialog de confirmación
    private AlertDialog dialogConfirmacion;
    private int horasExtension = 0;

    // Repository y datos
    private ReservaRepository reservaRepository;
    private SupabaseClient supabaseClient;
    private Reserva reservaActual;
    private EstacionamientosHomeAdapter estacionamientosAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Stripe se inicializa una sola vez en SmartParkingApp
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated - Inicializando HomeFragment");

        initViews(view);
        setupRecyclerView();

        // Inicializar PaymentSheet
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);

        // Obtener Repository y SupabaseClient
        reservaRepository = ReservaRepository.getInstance(requireContext());
        supabaseClient = new SupabaseClient();

        // Observar cambios en la reserva activa
        reservaRepository.getReservaActiva().observe(getViewLifecycleOwner(), this::actualizarEstadoReserva);

        // Configurar listeners
        cvEscanearBoleto.setOnClickListener(v -> iniciarEscaneoQR());
        btnMenos.setOnClickListener(v -> ajustarHoras(-1));
        btnMas.setOnClickListener(v -> ajustarHoras(1));
        btnReiniciar.setOnClickListener(v -> reiniciarHoras());
        btnAceptarExtension.setOnClickListener(v -> mostrarDialogConfirmacion());

        // Cargar estacionamientos
        cargarEstacionamientos();
    }

    private void initViews(View view) {
        // Estado de reserva
        tvEstadoReserva = view.findViewById(R.id.tvEstadoReserva);
        cvEscanearBoleto = view.findViewById(R.id.cvEscanearBoleto);
        cvEstadoReserva = view.findViewById(R.id.cvEstadoReserva);
        rvEstacionamientos = view.findViewById(R.id.rvEstacionamientos);
        tvEstacionamientosDisponibles = view.findViewById(R.id.tvEstacionamientosDisponibles);

        // Detalles de reserva
        layoutDetallesReserva = view.findViewById(R.id.layoutDetallesReserva);
        tvNombreEstacionamiento = view.findViewById(R.id.tvNombreEstacionamientoDetalle);
        tvSeccionCajon = view.findViewById(R.id.tvSeccionCajonDetalle);
        tvHorarioReserva = view.findViewById(R.id.tvHorarioReservaDetalle);
        tvPrecioHora = view.findViewById(R.id.tvPrecioHoraDetalle);

        // Extensión de tiempo
        layoutExtension = view.findViewById(R.id.layoutExtension);
        tvHorasExtension = view.findViewById(R.id.tvHorasExtension);
        btnMenos = view.findViewById(R.id.btnMenos);
        btnMas = view.findViewById(R.id.btnMas);
        btnReiniciar = view.findViewById(R.id.btnReiniciar);
        btnAceptarExtension = view.findViewById(R.id.btnAceptarExtension);
    }

    private void setupRecyclerView() {
        rvEstacionamientos.setLayoutManager(new LinearLayoutManager(getContext()));

        estacionamientosAdapter = new EstacionamientosHomeAdapter(new ArrayList<>(),
                new EstacionamientosHomeAdapter.OnEstacionamientoClickListener() {
                    @Override
                    public void onEstacionamientoClick(Estacionamiento estacionamiento) {
                        // Solo se permite una reserva a la vez (pendiente o confirmada)
                        if (reservaRepository.tieneReservaActiva()) {
                            String msg = reservaRepository.reservaEsPendiente()
                                    ? "Ya tienes una reserva pendiente de pago"
                                    : "Ya tienes una reserva activa";
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Navegar a selección de reserva
                        Intent intent = SeleccionReservaActivity.createIntent(requireContext(), estacionamiento);
                        startActivity(intent);
                    }
                });

        rvEstacionamientos.setAdapter(estacionamientosAdapter);
    }

    /**
     * Actualiza la UI según el estado de la reserva
     */
    private void actualizarEstadoReserva(Reserva reserva) {
        reservaActual = reserva;

        if (reserva == null || reserva.getId() == -1) {
            // Estado 1: No hay reserva
            mostrarEstado1_SinReserva();
        } else if (!reserva.isConfirmada()) {
            // Estado 2: Reserva pendiente
            mostrarEstado2_ReservaPendiente();
        } else if (reserva.getTokenEstacionamiento() == null || reserva.getTokenEstacionamiento().isEmpty()) {
            // Estado 3: Reserva activa sin escanear boleto
            mostrarEstado3_ActivaSinBoleto(reserva);
        } else {
            // Estado 4: Reserva activa con boleto escaneado
            mostrarEstado4_ActivaConBoleto(reserva);
        }
    }

    /**
     * Estado 1: Sin reserva activa
     */
    private void mostrarEstado1_SinReserva() {
        cvEscanearBoleto.setVisibility(View.GONE);
        cvEstadoReserva.setVisibility(View.VISIBLE);
        rvEstacionamientos.setVisibility(View.VISIBLE);
        layoutDetallesReserva.setVisibility(View.GONE);
        layoutExtension.setVisibility(View.GONE);
        tvEstacionamientosDisponibles.setVisibility(View.VISIBLE);

        tvEstadoReserva.setText("• No tienes ninguna reserva activa");
        tvEstadoReserva.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

        Log.d(TAG, "Estado 1: Sin reserva");
    }

    /**
     * Estado 2: Reserva pendiente de pago
     */
    private void mostrarEstado2_ReservaPendiente() {
        cvEscanearBoleto.setVisibility(View.GONE);
        cvEstadoReserva.setVisibility(View.VISIBLE);
        rvEstacionamientos.setVisibility(View.VISIBLE);
        layoutDetallesReserva.setVisibility(View.GONE);
        layoutExtension.setVisibility(View.GONE);
        tvEstacionamientosDisponibles.setVisibility(View.VISIBLE);

        tvEstadoReserva.setText("• Tienes una reserva pendiente de pago");
        tvEstadoReserva.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));

        Log.d(TAG, "Estado 2: Reserva pendiente");
    }

    /**
     * Estado 3: Reserva activa sin escanear boleto
     */
    private void mostrarEstado3_ActivaSinBoleto(Reserva reserva) {
        cvEscanearBoleto.setVisibility(View.VISIBLE);
        cvEstadoReserva.setVisibility(View.GONE);
        rvEstacionamientos.setVisibility(View.GONE);
        layoutDetallesReserva.setVisibility(View.VISIBLE);
        layoutExtension.setVisibility(View.GONE);
        tvEstacionamientosDisponibles.setVisibility(View.GONE);

        mostrarDetallesReserva(reserva);

        Log.d(TAG, "Estado 3: Reserva activa sin boleto");
    }

    /**
     * Estado 4: Reserva activa con boleto escaneado
     */
    private void mostrarEstado4_ActivaConBoleto(Reserva reserva) {
        cvEscanearBoleto.setVisibility(View.GONE);
        cvEstadoReserva.setVisibility(View.GONE);
        rvEstacionamientos.setVisibility(View.GONE);
        layoutDetallesReserva.setVisibility(View.VISIBLE);
        layoutExtension.setVisibility(View.VISIBLE);
        tvEstacionamientosDisponibles.setVisibility(View.GONE);

        mostrarDetallesReserva(reserva);

        Log.d(TAG, "Estado 4: Reserva activa con boleto");
    }

    /**
     * Muestra los detalles de la reserva activa
     */
    private void mostrarDetallesReserva(Reserva reserva) {
        tvNombreEstacionamiento.setText(reserva.getNombreEstacionamiento());
        tvSeccionCajon.setText("Sección " + reserva.getSeccion() + " - Cajón " + reserva.getNumeroCajon());

        String horario = formatearHorario(reserva.getTiempoInicio(), reserva.getTiempoFin());
        int cantidadHoras = calcularCantidadHoras(reserva.getTiempoInicio(), reserva.getTiempoFin());

        tvHorarioReserva.setText(horario + " (" + cantidadHoras + " hora" + (cantidadHoras > 1 ? "s" : "") + ")");
        tvPrecioHora.setText(String.format(Locale.getDefault(), "$%.2f/hr", reserva.getPrecioHora()));
    }

    /**
     * Carga la lista de estacionamientos disponibles
     */
    private void cargarEstacionamientos() {
        supabaseClient.obtenerEstacionamientos(new SupabaseClient.SupabaseCallbackEstacionamientos() {
            @Override
            public void onSuccess(List<Estacionamiento> estacionamientos) {
                if (!isAdded()) return;
                estacionamientosAdapter.actualizarEstacionamientos(estacionamientos);
                Log.d(TAG, "Estacionamientos cargados: " + estacionamientos.size());
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error al cargar estacionamientos: " + message);
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Error al cargar estacionamientos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== ESCANEO DE QR O BARRAS ====================

    /**
     * Inicia el escaneo de código QR
     */
    private void iniciarEscaneoQR() {
        // Verificar permiso de cámara
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Solicitar permiso
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            // Iniciar escáner
            IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt("Escanea el código QR del boleto");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarEscaneoQR();
            } else {
                Toast.makeText(requireContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Maneja el resultado del escaneo de QR o código de barras
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null) {
            if (result.getContents() != null) {
                String qrContent = result.getContents();
                Log.d(TAG, "QR escaneado: " + qrContent);

                // Guardar el token en la reserva
                guardarTokenEstacionamiento(qrContent);
            } else {
                Toast.makeText(requireContext(), "Escaneo cancelado", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Guarda el token del estacionamiento en la reserva
     */
    private void guardarTokenEstacionamiento(String token) {
        if (reservaActual == null) return;

        supabaseClient.actualizarTokenEstacionamiento(reservaActual.getId(), token,
                new SupabaseClient.SupabaseCallback() {
                    @Override
                    public void onSuccessUsuario() {
                        Log.d(TAG, "Token guardado exitosamente");
                        reservaActual.setTokenEstacionamiento(token);
                        reservaRepository.guardarReserva(reservaActual);
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Boleto registrado correctamente", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onErrorUsuario(String message) {
                        Log.e(TAG, "Error al guardar token: " + message);
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Error al registrar boleto", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ==================== EXTENSIÓN DE TIEMPO CON STRIPE ====================

    /**
     * Ajusta las horas de extensión
     */
    private void ajustarHoras(int cambio) {
        horasExtension += cambio;

        // Límites: mínimo 0, máximo 3
        if (horasExtension < 0) horasExtension = 0;
        if (horasExtension > 3) horasExtension = 3;

        tvHorasExtension.setText(String.valueOf(horasExtension));
        btnAceptarExtension.setEnabled(horasExtension > 0);
    }

    /**
     * Reinicia el contador de horas
     */
    private void reiniciarHoras() {
        horasExtension = 0;
        tvHorasExtension.setText("0");
        btnAceptarExtension.setEnabled(false);
    }

    /**
     * Muestra el dialog de confirmación de extensión
     */
    private void mostrarDialogConfirmacion() {
        if (horasExtension == 0 || reservaActual == null) return;

        // Calcular precios
        double subtotal = reservaActual.getPrecioHora() * horasExtension;
        double comision = subtotal * COMISION_EXTENSION;
        double total = subtotal + comision;
        this.montoExtension = total; // Guardar el monto para el pago

        // Crear dialog personalizado con el CardView existente
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirmar_extension, null);

        TextView tvHorasDialog = dialogView.findViewById(R.id.tvHorasExtensionDialog);
        TextView tvSubtotalDialog = dialogView.findViewById(R.id.tvSubtotalDialog);
        TextView tvComisionDialog = dialogView.findViewById(R.id.tvComisionDialog);
        TextView tvTotalDialog = dialogView.findViewById(R.id.tvTotalDialog);
        Button btnConfirmar = dialogView.findViewById(R.id.btnConfirmarExtension);

        tvHorasDialog.setText(String.valueOf(horasExtension));
        tvSubtotalDialog.setText(String.format(Locale.getDefault(), "$%.2f", subtotal));
        tvComisionDialog.setText(String.format(Locale.getDefault(), "$%.2f", comision));
        tvTotalDialog.setText(String.format(Locale.getDefault(), "$%.2f", total));

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        builder.setCancelable(true);

        dialogConfirmacion = builder.create();
        dialogConfirmacion.show();

        btnConfirmar.setOnClickListener(v -> {
            dialogConfirmacion.dismiss();
            verificarDisponibilidadExtension();
        });
    }

    /**
     * Antes de cobrar, revisa que nadie haya reservado el cajón en las horas que se quieren agregar.
     */
    private void verificarDisponibilidadExtension() {
        if (reservaActual == null) return;

        String finActual = reservaActual.getTiempoFin();
        String nuevoFin = calcularNuevoTiempoFin(finActual, horasExtension);

        btnAceptarExtension.setEnabled(false);
        btnAceptarExtension.setText("Verificando...");

        supabaseClient.verificarDisponibilidad(reservaActual.getIdCajon(), finActual, nuevoFin, reservaActual.getId(),
                new SupabaseClient.SupabaseCallbackDisponibilidad() {
                    @Override
                    public void onResultado(boolean disponible) {
                        if (!isAdded()) return;
                        if (disponible) {
                            iniciarPagoExtension();
                        } else {
                            btnAceptarExtension.setEnabled(true);
                            btnAceptarExtension.setText("Aceptar");
                            Toast.makeText(requireContext(),
                                    "El cajón ya está reservado en ese horario. Prueba con menos horas.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        btnAceptarExtension.setEnabled(true);
                        btnAceptarExtension.setText("Aceptar");
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Inicia el proceso de pago para la extensión
     */
    private void iniciarPagoExtension() {
        if (reservaActual == null || montoExtension <= 0) {
            Toast.makeText(requireContext(), "Error: Monto inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Deshabilitar botón temporalmente
        btnAceptarExtension.setEnabled(false);
        btnAceptarExtension.setText("Procesando...");

        // Llamar a la API para obtener el Client Secret
        ReservaApiClient.obtenerClientSecret(
                montoExtension,
                (long) reservaActual.getId(),
                reservaActual.getIdUsuario(),
                reservaActual.getIdEstacionamiento(),
                new ReservaApiClient.ClientSecretCallback() {
                    @Override
                    public void onSuccess(String secret, String customer) {
                        if (!isAdded()) return;
                        clientSecret = secret;
                        presentPaymentSheet();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Error al iniciar pago: " + error, Toast.LENGTH_LONG).show();
                        btnAceptarExtension.setEnabled(true);
                        btnAceptarExtension.setText("Aceptar");
                    }
                }
        );
    }

    /**
     * Muestra la interfaz de PaymentSheet de Stripe
     */
    private void presentPaymentSheet() {
        PaymentSheet.Configuration configuration =
                new PaymentSheet.Configuration.Builder("Smart Parking - Extensión")
                        .allowsDelayedPaymentMethods(false)
                        .build();

        paymentSheet.presentWithPaymentIntent(clientSecret, configuration);
    }

    /**
     * Maneja el resultado del flujo de Stripe
     */
    private void onPaymentSheetResult(PaymentSheetResult result) {
        // Restaurar estado del botón
        btnAceptarExtension.setEnabled(true);
        btnAceptarExtension.setText("Aceptar");

        if (result instanceof PaymentSheetResult.Completed) {
            Toast.makeText(requireContext(), "¡Pago realizado! Extendiendo tiempo...", Toast.LENGTH_LONG).show();
            procesarExtensionDespuesDePago();
        } else if (result instanceof PaymentSheetResult.Canceled) {
            Log.d(TAG, "Pago de extensión cancelado.");
            Toast.makeText(requireContext(), "Pago cancelado", Toast.LENGTH_SHORT).show();
        } else if (result instanceof PaymentSheetResult.Failed) {
            String error = ((PaymentSheetResult.Failed) result).getError().getMessage();
            Log.e(TAG, "Error en PaymentSheet: " + error);
            Toast.makeText(requireContext(), "Error de pago: " + error, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Procesa la extensión de tiempo después de un pago exitoso
     */
    private void procesarExtensionDespuesDePago() {
        if (reservaActual == null) return;

        // Calcular nuevo tiempo_fin
        String nuevoTiempoFin = calcularNuevoTiempoFin(reservaActual.getTiempoFin(), horasExtension);

        // Actualizar en Supabase
        supabaseClient.extenderReserva(reservaActual.getId(), nuevoTiempoFin,
                new SupabaseClient.SupabaseCallback() {
                    @Override
                    public void onSuccessUsuario() {
                        Log.d(TAG, "Reserva extendida exitosamente");
                        // Actualizar reserva local
                        reservaActual.setTiempoFin(nuevoTiempoFin);
                        reservaRepository.guardarReserva(reservaActual);
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                "Tiempo extendido: +" + horasExtension + " hora(s)",
                                Toast.LENGTH_LONG).show();

                        // Reiniciar contador
                        reiniciarHoras();
                    }

                    @Override
                    public void onErrorUsuario(String message) {
                        Log.e(TAG, "Error al extender reserva: " + message);
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Error al extender tiempo", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ==================== UTILIDADES ====================

    private String formatearHorario(String inicio, String fin) {
        try {
            Date fechaInicio = DateTimeUtils.parsearUTCALocal(inicio);
            Date fechaFin = DateTimeUtils.parsearUTCALocal(fin);

            return DateTimeUtils.formatearHoraLocal(fechaInicio) + " - " +
                    DateTimeUtils.formatearHoraLocal(fechaFin);
        } catch (Exception e) {
            return "Error en horario";
        }
    }

    private int calcularCantidadHoras(String inicio, String fin) {
        try {
            Date fechaInicio = DateTimeUtils.parsearUTCALocal(inicio);
            Date fechaFin = DateTimeUtils.parsearUTCALocal(fin);

            long diffMs = fechaFin.getTime() - fechaInicio.getTime();
            return (int) (diffMs / (1000 * 60 * 60));
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Calcula el nuevo tiempo_fin sumando las horas de extensión
     */
    private String calcularNuevoTiempoFin(String tiempoFinActual, int horasExtra) {
        try {
            return DateTimeUtils.agregarHorasAUTC(tiempoFinActual, horasExtra);
        } catch (Exception e) {
            Log.e(TAG, "Error al calcular nuevo tiempo fin", e);
            return tiempoFinActual;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - verificando expiración");
        reservaRepository.verificarExpiracion();
    }
}