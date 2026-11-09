package com.smartparking.pagos;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smartparking.R;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.model.ConfirmSetupIntentParams;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.payments.paymentlauncher.PaymentLauncher;
import com.stripe.android.payments.paymentlauncher.PaymentResult;
import com.stripe.android.view.CardMultilineWidget;

/**
 * Formulario para guardar una tarjeta nueva en Stripe.
 *
 * Usa CardMultilineWidget de Stripe: solo acepta dígitos, no deja escribir más de los que tiene
 * la marca, marca "El número de tarjeta no es válido." si no pasa la validación, y pide un
 * código postal de exactamente 5 dígitos. Los datos van directo del formulario a Stripe.
 *
 * Al guardar: pide un SetupIntent a la Edge Function metodos-pago (preparar_alta) y lo confirma
 * con PaymentLauncher (si la tarjeta pide 3D Secure, Stripe muestra la autenticación).
 * Devuelve RESULT_OK cuando la tarjeta quedó guardada.
 */
public class AgregarTarjetaActivity extends AppCompatActivity {

    private static final String TAG = "AgregarTarjetaActivity";

    private CardMultilineWidget cardWidget;
    private Button btnGuardar;
    private ProgressBar progress;
    private PaymentLauncher paymentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agregar_tarjeta);

        cardWidget = findViewById(R.id.cardWidget);
        btnGuardar = findViewById(R.id.btnGuardarTarjeta);
        progress = findViewById(R.id.progressGuardar);

        // Código postal obligatorio de 5 dígitos (formato numérico de 5, igual que en México)
        cardWidget.setShouldShowPostalCode(true);
        cardWidget.setPostalCodeRequired(true);
        cardWidget.setUsZipCodeRequired(true);

        PaymentConfiguration stripeConfig = PaymentConfiguration.getInstance(this);
        paymentLauncher = PaymentLauncher.Companion.create(this,
                stripeConfig.getPublishableKey(), stripeConfig.getStripeAccountId(), this::onResultado);

        btnGuardar.setOnClickListener(v -> guardar());
    }

    private void guardar() {
        // Marca en rojo los campos inválidos o incompletos, con su mensaje
        if (!cardWidget.validateAllFields()) {
            Toast.makeText(this, "Revisa los datos de la tarjeta", Toast.LENGTH_SHORT).show();
            return;
        }
        PaymentMethodCreateParams params = cardWidget.getPaymentMethodCreateParams();
        if (params == null) {
            Toast.makeText(this, "Revisa los datos de la tarjeta", Toast.LENGTH_SHORT).show();
            return;
        }

        mostrarCargando(true);
        MetodosPagoApiClient.prepararAlta(new MetodosPagoApiClient.AltaCallback() {
            @Override
            public void onSuccess(String setupIntentClientSecret, String customerId, String ephemeralKey) {
                if (isFinishing() || isDestroyed()) return;
                paymentLauncher.confirm(ConfirmSetupIntentParams.create(params, setupIntentClientSecret));
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                Toast.makeText(AgregarTarjetaActivity.this, "No se pudo guardar la tarjeta: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onResultado(PaymentResult result) {
        if (result instanceof PaymentResult.Completed) {
            Toast.makeText(this, "Tarjeta guardada", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else if (result instanceof PaymentResult.Canceled) {
            mostrarCargando(false);
        } else if (result instanceof PaymentResult.Failed) {
            mostrarCargando(false);
            Throwable error = ((PaymentResult.Failed) result).getThrowable();
            Log.e(TAG, "No se pudo guardar la tarjeta", error);
            Toast.makeText(this, "No se pudo guardar la tarjeta: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarCargando(boolean cargando) {
        progress.setVisibility(cargando ? View.VISIBLE : View.GONE);
        btnGuardar.setEnabled(!cargando);
        cardWidget.setEnabled(!cargando);
    }
}
