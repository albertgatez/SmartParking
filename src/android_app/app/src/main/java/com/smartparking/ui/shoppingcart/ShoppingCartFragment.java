package com.smartparking.ui.shoppingcart;

import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import com.smartparking.utils.DateTimeUtils;
import com.smartparking.pagos.ReservaApiClient;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smartparking.R;
import com.smartparking.reservas.Reserva;
import com.smartparking.reservas.ReservaRepository;

import java.util.Date;
import java.util.Locale;


/**
 * Fragment del carrito de compras que muestra la reserva pendiente
 * y permite confirmar el pago
 */
public class ShoppingCartFragment extends Fragment {

    private static final String TAG = "ShoppingCartFragment";
    private static final double COMISION_PORCENTAJE = 0.04; // 4%
    private static final String CUPON_DESCUENTO = "PROMO10"; // Cupón hardcodeado
    private static final double DESCUENTO_CUPON = 0.10; // 10%
    private PaymentSheet paymentSheet;
    private String clientSecret;
    private double montoTotalFinal = 0.0; // Monto final a pagar

    // Vistas
    private LinearLayout layoutReservaActiva, layoutSinReserva;
    private TextView tvTemporizador, tvHorario, tvMetodoPago, tvCuponAplicado;
    private TextView tvNombreEstacionamiento, tvSeccionCajon, tvPrecioPorHora, tvTipoCajon;
    private TextView tvSubtotal, tvComision, tvTotal;
    private Button btnConfirmarPago, btnAplicarCupon;
    private android.widget.EditText etCupon;

    // ViewModels
    private ReservaRepository reservaRepo;

    // Datos
    private Reserva reservaActual;
    private CountDownTimer countDownTimer;
    private boolean cuponAplicado = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Stripe se inicializa una sola vez en SmartParkingApp
        return inflater.inflate(R.layout.fragment_shoppingcart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated - Inicializando ShoppingCartFragment");

        initViews(view);

        // 2. Inicializa el paymentsheet
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);

        // Obtiene el Repository compartido de la Activity
        reservaRepo = ReservaRepository.getInstance(requireContext());

        Log.d(TAG, "ViewModel obtenido, verificando reserva activa actual...");

        // Verifica si ya hay una reserva en el Repository
        Reserva reservaExistente = reservaRepo.getReservaActiva().getValue();
        if (reservaExistente != null) {
            Log.d(TAG, "Reserva existente encontrada en ViewModel: ID=" + reservaExistente.getId());
            actualizarUI(reservaExistente);
        } else {
            Log.d(TAG, "No hay reserva en ViewModel al iniciar");
        }

        // Observa cambios en la reserva activa
        reservaRepo.getReservaActiva().observe(getViewLifecycleOwner(), reserva -> {
            Log.d(TAG, "Observer activado - Reserva: " + (reserva != null ? "ID=" + reserva.getId() + ", Estado=" + reserva.isConfirmada() : "null"));
            actualizarUI(reserva);
        });

        // Configuracion de botones
        btnConfirmarPago.setOnClickListener(v -> confirmarPago());
        btnAplicarCupon.setOnClickListener(v -> aplicarCupon());
    }

    private void initViews(View view) {
        layoutReservaActiva = view.findViewById(R.id.layoutReservaActiva);
        layoutSinReserva = view.findViewById(R.id.layoutSinReserva);
        tvTemporizador = view.findViewById(R.id.tvTemporizador);
        tvHorario = view.findViewById(R.id.tvHorario);
        tvMetodoPago = view.findViewById(R.id.tvMetodoPago);
        tvCuponAplicado = view.findViewById(R.id.tvCuponAplicado);
        tvNombreEstacionamiento = view.findViewById(R.id.tvNombreEstacionamiento);
        tvSeccionCajon = view.findViewById(R.id.tvSeccionCajon);
        tvPrecioPorHora = view.findViewById(R.id.tvPrecioPorHora);
        tvTipoCajon = view.findViewById(R.id.tvTipoCajon);
        tvSubtotal = view.findViewById(R.id.tvSubtotal);
        tvComision = view.findViewById(R.id.tvComision);
        tvTotal = view.findViewById(R.id.tvTotal);
        btnConfirmarPago = view.findViewById(R.id.btnConfirmarPago);
        btnAplicarCupon = view.findViewById(R.id.btnAplicarCupon);
        etCupon = view.findViewById(R.id.etCupon);
    }

    /**
     * Actualiza la UI según si hay reserva activa o no
     */
    private void actualizarUI(Reserva reserva) {
        // ... (código existente) ...
        Log.d(TAG, "actualizarUI llamado");
        Log.d(TAG, "  - Reserva: " + (reserva != null ? "Existe" : "null"));

        if (reserva != null) {
            Log.d(TAG, "  - ID: " + reserva.getId());
            Log.d(TAG, "  - Estado: " + (reserva.isConfirmada() ? "Confirmada" : "Pendiente"));
            Log.d(TAG, "  - Usuario: " + reserva.getIdUsuario());
        }

        if (reserva == null) {
            // No hay reserva
            Log.d(TAG, "Mostrando: Sin reserva");
            mostrarSinReserva();
        } else if (reserva.isConfirmada()) {
            // Reserva confirmada - no mostrar en carrito
            Log.d(TAG, "Mostrando: Sin reserva (reserva está confirmada)");
            mostrarSinReserva();
        } else {
            // Hay reserva pendiente
            Log.d(TAG, "Mostrando: Reserva activa pendiente");
            reservaActual = reserva;
            mostrarReservaActiva();
        }
    }

    /**
     * Muestra el mensaje de "No hay reservas pendientes"
     */
    private void mostrarSinReserva() {
        layoutReservaActiva.setVisibility(View.GONE);
        layoutSinReserva.setVisibility(View.VISIBLE);
        detenerTemporizador();
    }

    /**
     * Muestra la reserva activa con todos sus detalles
     */
    private void mostrarReservaActiva() {
        layoutReservaActiva.setVisibility(View.VISIBLE);
        layoutSinReserva.setVisibility(View.GONE);

        // Iniciar temporizador
        iniciarTemporizador();

        // Mostrar horario
        String horario = formatearHorario(reservaActual.getTiempoInicio(), reservaActual.getTiempoFin());
        int cantidadHoras = calcularCantidadHoras(reservaActual.getTiempoInicio(), reservaActual.getTiempoFin());
        tvHorario.setText(String.format("Horario\n$%.2f/hr | %s\n%s | %d horas",
                reservaActual.getPrecioHora(),
                reservaActual.isDiscapacitado() ? "Discapacitado" : "Estándar",
                horario,
                cantidadHoras));

        // Método de pago (simulado)
        tvMetodoPago.setText("Visa **** **** **** 1234");

        // Información de la reserva
        tvNombreEstacionamiento.setText(reservaActual.getNombreEstacionamiento());
        tvSeccionCajon.setText("Sección: " + reservaActual.getSeccion());
        tvPrecioPorHora.setText("Lugar: " + reservaActual.getSeccion() + reservaActual.getNumeroCajon());
        tvTipoCajon.setText(reservaActual.isDiscapacitado() ? "Discapacitado" : "Estándar");

        // Calcular precios
        calcularYMostrarPrecios(cantidadHoras);

    }

    /**
     * Inicia el temporizador de 5 minutos
     */
    private void iniciarTemporizador() {
        detenerTemporizador();

        if (reservaActual == null) return;

        long tiempoRestanteMs = reservaActual.getExpiracionMs() - System.currentTimeMillis();

        if (tiempoRestanteMs <= 0) {
            reservaExpirada();
            return;
        }

        countDownTimer = new CountDownTimer(tiempoRestanteMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutos = (millisUntilFinished / 1000) / 60;
                long segundos = (millisUntilFinished / 1000) % 60;
                tvTemporizador.setText(String.format(Locale.getDefault(),
                        "Tiempo restante: %02d:%02d min", minutos, segundos));
            }

            @Override
            public void onFinish() {
                reservaExpirada();
            }
        };

        countDownTimer.start();
    }

    /**
     * Maneja la expiración de la reserva
     */
    private void reservaExpirada() {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "El tiempo para completar la reserva ha expirado", Toast.LENGTH_LONG).show();

        // Elimina la reserva del ViewModel (esto también la elimina de la BD)
        reservaRepo.limpiarReserva(true);

        detenerTemporizador();
        mostrarSinReserva();
    }

    /**
     * Detiene el temporizador si está corriendo
     */
    private void detenerTemporizador() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    /**
     * Formatea el horario de inicio y fin
     */
    private String formatearHorario(String inicio, String fin) {
        try {
            Date fechaInicio = DateTimeUtils.parsearUTCALocal(inicio);
            Date fechaFin = DateTimeUtils.parsearUTCALocal(fin);

            return DateTimeUtils.formatearHoraLocal(fechaInicio) + " - " +
                    DateTimeUtils.formatearHoraLocal(fechaFin);
        } catch (Exception e) {
            Log.e(TAG, "Error al formatear horario", e);
            return "Error en horario";
        }
    }

    /**
     * Calcula la cantidad de horas entre inicio y fin
     */
    private int calcularCantidadHoras(String inicio, String fin) {
        try {
            Date fechaInicio = DateTimeUtils.parsearUTCALocal(inicio);
            Date fechaFin = DateTimeUtils.parsearUTCALocal(fin);

            long diffMs = fechaFin.getTime() - fechaInicio.getTime();
            return (int) (diffMs / (1000 * 60 * 60));
        } catch (Exception e) {
            Log.e(TAG, "Error al calcular horas", e);
            return 1;
        }
    }

    /**
     * Calcula y muestra el subtotal, comisión y total.
     * Guarda el total en montoTotalFinal.
     */
    private void calcularYMostrarPrecios(int cantidadHoras) {
        double subtotal = reservaActual.getPrecioHora() * cantidadHoras;
        double comision = subtotal * COMISION_PORCENTAJE;
        double descuento = cuponAplicado ? subtotal * DESCUENTO_CUPON : 0;
        double total = subtotal + comision - descuento;

        this.montoTotalFinal = total; // Guarda el monto final a pagar

        tvSubtotal.setText(String.format(Locale.getDefault(), "$%.2f", subtotal));
        tvComision.setText(String.format(Locale.getDefault(), "$%.2f", comision));
        tvTotal.setText(String.format(Locale.getDefault(), "$%.2f", total));

        if (cuponAplicado) {
            tvCuponAplicado.setVisibility(View.VISIBLE);
            tvCuponAplicado.setText(String.format(Locale.getDefault(),
                    "Descuento (%s): -$%.2f", CUPON_DESCUENTO, descuento));
        }
    }

    /**
     * Aplica un cupón de descuento
     */
    private void aplicarCupon() {
        String cuponIngresado = etCupon.getText().toString().trim();

        if (cuponIngresado.equals(CUPON_DESCUENTO)) {
            cuponAplicado = true;
            int cantidadHoras = calcularCantidadHoras(reservaActual.getTiempoInicio(), reservaActual.getTiempoFin());
            calcularYMostrarPrecios(cantidadHoras);
            Toast.makeText(requireContext(), "Cupón aplicado correctamente", Toast.LENGTH_SHORT).show();
            etCupon.setEnabled(false);
            btnAplicarCupon.setEnabled(false);
        } else {
            Toast.makeText(requireContext(), "Cupón inválido", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Inicia el proceso de pago llamando al backend para obtener el Client Secret de Stripe.
     */
    private void confirmarPago() {
        if (reservaActual == null || montoTotalFinal <= 0) {
            Toast.makeText(requireContext(), "Error: Reserva o monto inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Deshabilitar botón y detener temporizador
        btnConfirmarPago.setEnabled(false);
        btnConfirmarPago.setText("Procesando...");
        detenerTemporizador();

        // Llama al API para obtener el Client Secret
        ReservaApiClient.obtenerClientSecret(
                montoTotalFinal,
                (long) reservaActual.getId(),
                reservaActual.getIdUsuario(),
                reservaActual.getIdEstacionamiento(),
                new ReservaApiClient.ClientSecretCallback() {
                    @Override
                    public void onSuccess(String secret, String customer) {
                        if (!isAdded()) return;
                        clientSecret = secret;
                        presentPaymentSheet(); // 2. Mostrar la interfaz de Stripe
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Error al iniciar pago: " + error, Toast.LENGTH_LONG).show();
                        btnConfirmarPago.setEnabled(true);
                        btnConfirmarPago.setText("Confirmar pago");
                        iniciarTemporizador(); // Reanudar el temporizador
                    }
                }
        );
    }

    /**
     * Muestra la interfaz de PaymentSheet de Stripe
     */
    private void presentPaymentSheet() {
        PaymentSheet.Configuration configuration =
                new PaymentSheet.Configuration.Builder("Smart Parking")
                        .allowsDelayedPaymentMethods(false)
                        .build();

        paymentSheet.presentWithPaymentIntent(clientSecret, configuration);
    }

    /**
     * Maneja el resultado del flujo de Stripe.
     */
    private void onPaymentSheetResult(PaymentSheetResult result) {
        btnConfirmarPago.setEnabled(true);
        btnConfirmarPago.setText("Confirmar pago"); // Resetear el texto del botón

        if (result instanceof PaymentSheetResult.Completed) {
            Toast.makeText(requireContext(), "¡Pago realizado!", Toast.LENGTH_LONG).show();

            // Confirmar la reserva en BD y limpiar la reserva local
            reservaRepo.confirmarReservaActual();
            mostrarSinReserva();

        } else if (result instanceof PaymentSheetResult.Canceled) {
            Log.d(TAG, "Pago cancelado.");
            Toast.makeText(requireContext(), "Pago cancelado", Toast.LENGTH_SHORT).show();
            iniciarTemporizador(); // Reanudar temporizador tras cancelación

        } else if (result instanceof PaymentSheetResult.Failed) {
            String error = ((PaymentSheetResult.Failed) result).getError().getMessage();
            Log.e(TAG, "Error en PaymentSheet: " + error);
            Toast.makeText(requireContext(), "Error de pago: " + error, Toast.LENGTH_LONG).show();
            iniciarTemporizador(); // Reanudar temporizador tras fallo
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detenerTemporizador();
    }
}