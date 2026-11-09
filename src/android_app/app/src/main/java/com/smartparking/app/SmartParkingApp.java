package com.smartparking.app;

import android.app.Application;
import android.util.Log;

import com.smartparking.login.SessionManager;
import com.smartparking.notificaciones.NotificationHelper;
import com.stripe.android.PaymentConfiguration;
import com.smartparking.BuildConfig;

/**
 * Punto de entrada de la aplicación. Se ejecuta una sola vez al abrir la app.
 * Registrada en AndroidManifest.xml con android:name=".app.SmartParkingApp".
 */
public class SmartParkingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        SessionManager.init(this);

        // Barra de estado azul también en Android 15+ (edge-to-edge)
        BarraDeEstado.registrar(this);

        // Canal de notificaciones (obligatorio para mostrar notificaciones en Android 8+)
        NotificationHelper.crearCanales(this);

        // Stripe se configura una sola vez para toda la app.
        // La llave publicable se lee de local.properties (STRIPE_PUBLISHABLE_KEY).
        String stripeKey = BuildConfig.STRIPE_PUBLISHABLE_KEY;
        if (stripeKey != null && stripeKey.startsWith("pk_")) {
            PaymentConfiguration.init(this, stripeKey);
        } else {
            Log.e("SmartParkingApp", "STRIPE_PUBLISHABLE_KEY no está configurada en local.properties; los pagos no funcionarán");
        }
    }
}