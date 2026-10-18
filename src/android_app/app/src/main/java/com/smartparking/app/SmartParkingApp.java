package com.smartparking.app;

import android.app.Application;
import android.util.Log;

import com.smartparking.login.SessionManager;
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
