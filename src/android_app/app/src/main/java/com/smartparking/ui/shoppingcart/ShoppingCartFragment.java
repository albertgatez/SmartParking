package com.smartparking.ui.shoppingcart;

import com.stripe.android.PaymentConfiguration;
import com.stripe.android.model.ConfirmPaymentIntentParams;
import com.stripe.android.payments.paymentlauncher.PaymentLauncher;
import com.stripe.android.payments.paymentlauncher.PaymentResult;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import com.smartparking.utils.DateTimeUtils;
import com.smartparking.pagos.DesglosePago;
import com.smartparking.pagos.MetodoPago;
import com.smartparking.pagos.MetodosPagoActivity;
import com.smartparking.pagos.MetodosPagoApiClient;
import com.smartparking.pagos.PreferenciasPago;
import com.smartparking.pagos.ReservaApiClient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private PaymentLauncher paymentLauncher;
    private String clientSecret;

    // Método de pago: la tarjeta guardada con la que se va a pagar (null = no tiene o no cargó)
    private MetodoPago metodoSeleccionado = null;
    private ActivityResultLauncher<Intent> seleccionTarjetaLauncher;

    // Promociones: búsqueda del cupón mientras se escribe (con espera para no llamar en cada tecla)
    private static final long ESPERA_BUSQUEDA_CUPON_MS = 600;
    private final Handler handlerCupon = new Handler(Looper.getMainLooper());
    private Runnable busquedaCuponPendiente;
    private DesglosePago sugerenciaCupon; // desglose del cupón sugerido (válido)
    private View layoutSugerenciaCupon;
    private TextView tvSugerenciaCodigo, tvSugerenciaDescripcion, tvCuponInvalido;

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

        // 2. Launcher de Stripe para pagar con la tarjeta guardada elegida
        PaymentConfiguration stripeConfig = PaymentConfiguration.getInstance(requireContext());
        paymentLauncher = PaymentLauncher.Companion.create(this,
                stripeConfig.getPublishableKey(), stripeConfig.getStripeAccountId(), this::onPaymentResult);

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
        // "Aplicar" valida el cupón escrito; "Quitar" lo quita
        btnAplicarCupon.setOnClickListener(v -> {
            if (cuponAplicado != null) quitarCupon(); else aplicarCupon();
        });

        // La fila de pago abre Métodos de pago para elegir la tarjeta o agregar otra
        view.findViewById(R.id.filaMetodoPago).setOnClickListener(v -> abrirSelectorTarjeta(null));

        // Promociones: al escribir, se busca el cupón en el servidor y se sugiere si es válido
        configurarBusquedaCupon(view);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Debe registrarse antes de que el fragment se cree
        seleccionTarjetaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String id = result.getData().getStringExtra(MetodosPagoActivity.EXTRA_ID_METODO);
                        // Se guarda para que no se pierda al cambiar de pestaña
                        if (reservaActual != null) {
                            PreferenciasPago.setTarjeta(requireContext(), reservaActual.getId(), id);
                        }
                    }
                    // onResume recarga la fila PAGO con la tarjeta elegida
                });
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
        layoutSugerenciaCupon = view.findViewById(R.id.layoutSugerenciaCupon);
        tvSugerenciaCodigo = view.findViewById(R.id.tvSugerenciaCodigo);
        tvSugerenciaDescripcion = view.findViewById(R.id.tvSugerenciaDescripcion);
        tvCuponInvalido = view.findViewById(R.id.tvCuponInvalido);
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
     * Carga las tarjetas guardadas en Stripe y selecciona la que eligió el usuario en
     * Métodos de pago (si sigue existiendo) o, si no eligió, la predeterminada.
     */
    private void cargarMetodoPago() {
        tvMetodoPago.setText("Cargando...");

        MetodosPagoApiClient.listar(new MetodosPagoApiClient.ListaCallback() {
            @Override
            public void onSuccess(List<MetodoPago> metodos) {
                if (!isAdded()) return;
                // La tarjeta elegida para esta reserva (guardada en el teléfono), si sigue existiendo
                String idElegida = reservaActual != null
                        ? PreferenciasPago.getTarjeta(requireContext(), reservaActual.getId()) : null;
                MetodoPago elegida = MetodoPago.buscarPorId(metodos, idElegida);
                metodoSeleccionado = elegida != null ? elegida : MetodoPago.buscarPredeterminada(metodos);
                tvMetodoPago.setText(MetodoPago.textoFilaPago(metodoSeleccionado));
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                Log.w(TAG, "No se pudo cargar el método de pago: " + error);
                metodoSeleccionado = null;
                tvMetodoPago.setText("No se pudo cargar el método de pago\nToca para reintentar");
            }
        });
    }

    /** Abre Métodos de pago en modo selección: ver tarjetas, elegir con cuál pagar o agregar otra. */
    private void abrirSelectorTarjeta(@Nullable String mensaje) {
        String idActual = metodoSeleccionado != null ? metodoSeleccionado.getId() : null;
        seleccionTarjetaLauncher.launch(MetodosPagoActivity.intentSeleccion(requireContext(), idActual, mensaje));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver de Métodos de pago (eligió, agregó o eliminó tarjetas) se recarga la fila PAGO
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

        // Al mostrar otra reserva (o al volver a esta pestaña) se recupera el cupón guardado
        // para esta reserva; si es otra reserva, PreferenciasPago descarta el anterior
        if (reservaActual.getId() != idReservaCupon) {
            idReservaCupon = reservaActual.getId();
            cancelarBusquedaCupon();
            sugerenciaCupon = null;
            layoutSugerenciaCupon.setVisibility(View.GONE);
            tvCuponInvalido.setVisibility(View.GONE);
            mostrarEstadoCupon(PreferenciasPago.getCupon(requireContext(), idReservaCupon));
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
        PreferenciasPago.limpiar(requireContext());

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
                        // Si el cupón guardado ya no es válido, se quita y se vuelve a calcular sin él
                        if (cuponAplicado != null && error.toLowerCase(Locale.ROOT).contains("cup")) {
                            Toast.makeText(requireContext(), "El cupón " + cuponAplicado + " ya no es válido", Toast.LENGTH_LONG).show();
                            quitarCupon();
                            return;
                        }
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
                        if (desglose.getCupon() != null) {
                            aplicarDesgloseConCupon(desglose);
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

    /** Deja aplicado el cupón del desglose (ya validado por el servidor) y lo guarda. */
    private void aplicarDesgloseConCupon(DesglosePago desglose) {
        cancelarBusquedaCupon();
        layoutSugerenciaCupon.setVisibility(View.GONE);
        tvCuponInvalido.setVisibility(View.GONE);
        mostrarEstadoCupon(desglose.getCupon());
        mostrarPrecios(desglose);
        if (reservaActual != null) {
            PreferenciasPago.setCupon(requireContext(), reservaActual.getId(), cuponAplicado);
        }
        Toast.makeText(requireContext(), "Cupón aplicado correctamente", Toast.LENGTH_SHORT).show();
    }

    /** Quita el cupón aplicado, lo borra de lo guardado y recalcula el precio sin descuento. */
    private void quitarCupon() {
        if (reservaActual != null) {
            PreferenciasPago.setCupon(requireContext(), reservaActual.getId(), null);
        }
        mostrarEstadoCupon(null);
        tvCuponAplicado.setVisibility(View.GONE);
        cargarPrecios();
    }

    /**
     * Ajusta el campo y el botón según haya o no cupón:
     * con cupón, el campo queda bloqueado con el código y el botón dice "Quitar";
     * sin cupón, el campo queda vacío y el botón dice "Aplicar".
     */
    private void mostrarEstadoCupon(@Nullable String cupon) {
        cuponAplicado = cupon;
        // Se cambia "enabled" antes que el texto para que el cambio no dispare una búsqueda
        if (cupon != null) {
            etCupon.setEnabled(false);
            etCupon.setText(cupon);
            btnAplicarCupon.setText("Quitar");
        } else {
            etCupon.setEnabled(false);
            etCupon.setText("");
            etCupon.setEnabled(true);
            btnAplicarCupon.setText("Aplicar");
        }
        btnAplicarCupon.setEnabled(true);
    }

    // ---------- Promociones: sugerencia mientras se escribe ----------

    private void configurarBusquedaCupon(View view) {
        // Al tocar la sugerencia se aplica la promoción
        layoutSugerenciaCupon.setOnClickListener(v -> {
            if (sugerenciaCupon != null) aplicarDesgloseConCupon(sugerenciaCupon);
        });

        etCupon.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (!etCupon.isEnabled()) return; // ya hay un cupón aplicado
                cancelarBusquedaCupon();
                sugerenciaCupon = null;
                layoutSugerenciaCupon.setVisibility(View.GONE);
                tvCuponInvalido.setVisibility(View.GONE);

                String codigo = s.toString().trim().toUpperCase(Locale.ROOT);
                if (codigo.length() < 3) return; // los códigos tienen al menos 3 caracteres

                busquedaCuponPendiente = () -> buscarCupon(codigo);
                handlerCupon.postDelayed(busquedaCuponPendiente, ESPERA_BUSQUEDA_CUPON_MS);
            }
        });
    }

    private void cancelarBusquedaCupon() {
        if (busquedaCuponPendiente != null) {
            handlerCupon.removeCallbacks(busquedaCuponPendiente);
            busquedaCuponPendiente = null;
        }
    }

    /** Pregunta al servidor si el código es válido; si lo es, muestra la sugerencia. */
    private void buscarCupon(String codigo) {
        if (reservaActual == null) return;
        final long idReserva = reservaActual.getId();

        ReservaApiClient.cotizar(idReserva, ReservaApiClient.TIPO_RESERVA, 0, codigo,
                new ReservaApiClient.CotizacionCallback() {
                    @Override
                    public void onSuccess(DesglosePago desglose) {
                        if (!esBusquedaVigente(idReserva, codigo)) return;
                        if (desglose.getCupon() == null) {
                            tvCuponInvalido.setVisibility(View.VISIBLE);
                            return;
                        }
                        sugerenciaCupon = desglose;
                        tvSugerenciaCodigo.setText(String.format(Locale.getDefault(),
                                "%s  ·  -$%.2f", desglose.getCupon(), desglose.getDescuento()));
                        tvSugerenciaDescripcion.setText(desglose.getCuponDescripcion());
                        layoutSugerenciaCupon.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError(String error) {
                        if (!esBusquedaVigente(idReserva, codigo)) return;
                        // "Cupón inválido" u otro error: se avisa debajo del campo
                        tvCuponInvalido.setVisibility(View.VISIBLE);
                    }
                });
    }

    /** La respuesta sirve solo si sigue la misma reserva y el mismo texto en el campo. */
    private boolean esBusquedaVigente(long idReserva, String codigo) {
        return isAdded() && reservaActual != null && reservaActual.getId() == idReserva
                && etCupon.isEnabled()
                && codigo.equals(etCupon.getText().toString().trim().toUpperCase(Locale.ROOT));
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

        // Se paga con una tarjeta guardada; si no hay, se abre Métodos de pago para elegir o agregar
        final MetodoPago tarjeta = metodoSeleccionado;
        if (tarjeta == null) {
            Toast.makeText(requireContext(), "Elige o agrega una tarjeta para pagar", Toast.LENGTH_SHORT).show();
            abrirSelectorTarjeta("Agrega o elige la tarjeta con la que vas a pagar tu reserva.");
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
                        mostrarPrecios(desglose); // Lo que se va a cobrar
                        cobrar(tarjeta); // 2. Cobrar con la tarjeta elegida
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
     * Confirma el pago con la tarjeta guardada elegida (Stripe pide 3D Secure si la tarjeta
     * lo necesita). confirmarPago() ya verificó que hay una tarjeta elegida.
     */
    private void cobrar(MetodoPago tarjeta) {
        Log.d(TAG, "Pagando con la tarjeta guardada " + tarjeta.getId());
        paymentLauncher.confirm(ConfirmPaymentIntentParams.createWithPaymentMethodId(
                tarjeta.getId(), clientSecret));
    }

    /** Resultado del pago; se convierte a PaymentSheetResult para reutilizar el mismo manejo. */
    private void onPaymentResult(PaymentResult result) {
        if (result instanceof PaymentResult.Completed) {
            onPaymentSheetResult(PaymentSheetResult.Completed.INSTANCE);
        } else if (result instanceof PaymentResult.Canceled) {
            onPaymentSheetResult(PaymentSheetResult.Canceled.INSTANCE);
        } else if (result instanceof PaymentResult.Failed) {
            onPaymentSheetResult(new PaymentSheetResult.Failed(((PaymentResult.Failed) result).getThrowable()));
        }
    }

    /**
     * Maneja el resultado del flujo de Stripe.
     */
    private void onPaymentSheetResult(PaymentSheetResult result) {
        btnConfirmarPago.setEnabled(true);
        btnConfirmarPago.setText("Confirmar pago"); // Resetear el texto del botón

        if (result instanceof PaymentSheetResult.Completed) {
            Toast.makeText(requireContext(), "¡Pago realizado!", Toast.LENGTH_LONG).show();

            // Ya se pagó: se borran el cupón y la tarjeta guardados para esta reserva
            PreferenciasPago.limpiar(requireContext());

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
        cancelarBusquedaCupon();
    }
}