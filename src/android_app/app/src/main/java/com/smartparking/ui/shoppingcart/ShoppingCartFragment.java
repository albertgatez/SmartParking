package com.smartparking.ui.shoppingcart;

import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import com.smartparking.utils.DateTimeUtils;
import com.smartparking.pagos.DesglosePago;
import com.smartparking.pagos.MetodoPago;
import com.smartparking.pagos.MetodosPagoActivity;
import com.smartparking.pagos.MetodosPagoApiClient;
import com.smartparking.pagos.ReservaApiClient;

import android.content.Intent;
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
import java.util.List;
import java.util.Locale;


/**
 * Fragment del carrito de compras que muestra la reserva pendiente
 * y permite confirmar el pago
 */
public class ShoppingCartFragment extends Fragment {

    private static final String TAG = "ShoppingCartFragment";
    private PaymentSheet paymentSheet;
    private String clientSecret;
    private String customerIdPago; // Cliente de Stripe del PaymentIntent

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

    // Cupón validado por el servidor (null si no hay) y reserva a la que se aplicó
    private String cuponAplicado = null;
    private long idReservaCupon = -1;

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

        // La fila de pago abre Perfil > Métodos de pago; al volver, onResume recarga la tarjeta
        view.findViewById(R.id.filaMetodoPago).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MetodosPagoActivity.class)));
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
     * Muestra la tarjeta que el usuario tiene guardada en Stripe (la más reciente)
     * o avisa que no tiene ninguna. Al pagar, la tarjeta se elige en el PaymentSheet.
     */
    private void cargarMetodoPago() {
        tvMetodoPago.setText("Cargando...");

        MetodosPagoApiClient.listar(new MetodosPagoApiClient.ListaCallback() {
            @Override
            public void onSuccess(List<MetodoPago> metodos) {
                if (!isAdded()) return;
                if (metodos.isEmpty()) {
                    tvMetodoPago.setText("Sin método de pago guardado");
                    return;
                }
                MetodoPago tarjeta = metodos.get(0);
                String texto = tarjeta.getMarcaVisible() + " •••• " + tarjeta.getUltimos4()
                        + "  (vence " + tarjeta.getVencimiento() + ")";
                if (metodos.size() > 1) {
                    texto += "\n+" + (metodos.size() - 1) + " tarjeta(s) más";
                }
                tvMetodoPago.setText(texto);
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Log.w(TAG, "No se pudo cargar el método de pago: " + error);
                tvMetodoPago.setText("No se pudo cargar el método de pago");
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Por si el usuario agregó o eliminó tarjetas en Perfil > Métodos de pago
        if (layoutReservaActiva != null && layoutReservaActiva.getVisibility() == View.VISIBLE) {
            cargarMetodoPago();
        }
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

        // Tarjeta guardada en Stripe (o aviso de que no tiene)
        cargarMetodoPago();

        // Información de la reserva
        tvNombreEstacionamiento.setText(reservaActual.getNombreEstacionamiento());
        tvSeccionCajon.setText("Sección: " + reservaActual.getSeccion());
        tvPrecioPorHora.setText("Lugar: " + reservaActual.getSeccion() + reservaActual.getNumeroCajon());
        tvTipoCajon.setText(reservaActual.isDiscapacitado() ? "Discapacitado" : "Estándar");

        // Si es otra reserva, el cupón anterior ya no aplica
        if (reservaActual.getId() != idReservaCupon) {
            cuponAplicado = null;
            idReservaCupon = reservaActual.getId();
            etCupon.setText("");
            etCupon.setEnabled(true);
            btnAplicarCupon.setEnabled(true);
            tvCuponAplicado.setVisibility(View.GONE);
        }

        // Precios calculados por el servidor
        cargarPrecios();
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
     * Pide al servidor el subtotal, la comisión y el total (con el cupón aplicado, si hay).
     * La app no calcula precios: solo muestra lo que calcula crear-pago-reserva-v2.
     */
    private void cargarPrecios() {
        if (reservaActual == null) return;
        final long idReserva = reservaActual.getId();

        tvSubtotal.setText("...");
        tvComision.setText("...");
        tvTotal.setText("Calculando...");

        ReservaApiClient.cotizar(idReserva, ReservaApiClient.TIPO_RESERVA, 0, cuponAplicado,
                new ReservaApiClient.CotizacionCallback() {
                    @Override
                    public void onSuccess(DesglosePago desglose) {
                        if (!isAdded() || reservaActual == null || reservaActual.getId() != idReserva) return;
                        mostrarPrecios(desglose);
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded() || reservaActual == null || reservaActual.getId() != idReserva) return;
                        Log.e(TAG, "No se pudo calcular el precio: " + error);
                        tvSubtotal.setText("—");
                        tvComision.setText("—");
                        tvTotal.setText("—");
                        Toast.makeText(requireContext(), "No se pudo calcular el precio: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Muestra el desglose calculado por el servidor.
     */
    private void mostrarPrecios(DesglosePago desglose) {
        tvSubtotal.setText(String.format(Locale.getDefault(), "$%.2f", desglose.getSubtotal()));
        tvComision.setText(String.format(Locale.getDefault(), "$%.2f", desglose.getComision()));
        tvTotal.setText(String.format(Locale.getDefault(), "$%.2f", desglose.getTotal()));

        if (desglose.getCupon() != null) {
            tvCuponAplicado.setVisibility(View.VISIBLE);
            tvCuponAplicado.setText(String.format(Locale.getDefault(),
                    "Descuento (%s): -$%.2f", desglose.getCupon(), desglose.getDescuento()));
        } else {
            tvCuponAplicado.setVisibility(View.GONE);
        }
    }

    /**
     * Valida el cupón en el servidor. Si es válido, se muestra el nuevo desglose
     * y el cupón se manda también al crear el pago.
     */
    private void aplicarCupon() {
        if (reservaActual == null) return;
        String cuponIngresado = etCupon.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (cuponIngresado.isEmpty()) {
            Toast.makeText(requireContext(), "Escribe un cupón", Toast.LENGTH_SHORT).show();
            return;
        }

        final long idReserva = reservaActual.getId();
        btnAplicarCupon.setEnabled(false);

        ReservaApiClient.cotizar(idReserva, ReservaApiClient.TIPO_RESERVA, 0, cuponIngresado,
                new ReservaApiClient.CotizacionCallback() {
                    @Override
                    public void onSuccess(DesglosePago desglose) {
                        if (!isAdded() || reservaActual == null || reservaActual.getId() != idReserva) return;
                        cuponAplicado = desglose.getCupon();
                        mostrarPrecios(desglose);
                        if (cuponAplicado != null) {
                            Toast.makeText(requireContext(), "Cupón aplicado correctamente", Toast.LENGTH_SHORT).show();
                            etCupon.setEnabled(false);
                        } else {
                            Toast.makeText(requireContext(), "Cupón inválido", Toast.LENGTH_SHORT).show();
                            btnAplicarCupon.setEnabled(true);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        btnAplicarCupon.setEnabled(true);
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Inicia el proceso de pago llamando al backend para obtener el Client Secret de Stripe.
     * El monto lo calcula el servidor; la app solo manda la reserva y el cupón.
     */
    private void confirmarPago() {
        if (reservaActual == null) {
            Toast.makeText(requireContext(), "Error: No hay reserva para pagar.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Deshabilitar botón y detener temporizador
        btnConfirmarPago.setEnabled(false);
        btnConfirmarPago.setText("Procesando...");
        detenerTemporizador();

        // Llama al API para obtener el Client Secret
        ReservaApiClient.obtenerClientSecret(
                (long) reservaActual.getId(),
                ReservaApiClient.TIPO_RESERVA,
                0,
                cuponAplicado,
                new ReservaApiClient.ClientSecretCallback() {
                    @Override
                    public void onSuccess(String secret, String customer, DesglosePago desglose) {
                        if (!isAdded()) return;
                        clientSecret = secret;
                        customerIdPago = customer;
                        mostrarPrecios(desglose); // Lo que se va a cobrar
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
     * Muestra la interfaz de PaymentSheet de Stripe. Antes pide la llave del cliente para
     * mostrar sus tarjetas guardadas; si falla, se paga como siempre (sin tarjetas guardadas).
     */
    private void presentPaymentSheet() {
        MetodosPagoApiClient.obtenerLlaveCliente(new MetodosPagoApiClient.LlaveClienteCallback() {
            @Override
            public void onSuccess(String customerId, String ephemeralKey) {
                if (!isAdded()) return;
                // Las tarjetas guardadas solo sirven si el pago es del mismo cliente de Stripe
                if (customerId.equals(customerIdPago)) {
                    mostrarPaymentSheet(new PaymentSheet.CustomerConfiguration(customerId, ephemeralKey));
                } else {
                    Log.w(TAG, "El pago no es del mismo cliente de Stripe; se omiten las tarjetas guardadas");
                    mostrarPaymentSheet(null);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Log.w(TAG, "Sin tarjetas guardadas: " + error);
                mostrarPaymentSheet(null);
            }
        });
    }

    private void mostrarPaymentSheet(PaymentSheet.CustomerConfiguration cliente) {
        PaymentSheet.Configuration.Builder builder =
                new PaymentSheet.Configuration.Builder("Smart Parking")
                        .allowsDelayedPaymentMethods(false);
        if (cliente != null) builder.customer(cliente);

        paymentSheet.presentWithPaymentIntent(clientSecret, builder.build());
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