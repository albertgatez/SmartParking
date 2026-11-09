package com.smartparking.app;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.smartparking.R;

/**
 * Pinta la barra de estado con el azul de marca en todas las pantallas.
 * Desde Android 15 (API 35) la app se dibuja de punta a punta y el sistema ignora
 * statusBarColor; por eso el color se aplica con EdgeToEdge y el contenido se baja
 * debajo de la barra para que no quede tapado.
 */
public final class BarraDeEstado {

    private BarraDeEstado() {
    }

    public static void registrar(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (activity instanceof ComponentActivity) {
                    int azul = ContextCompat.getColor(activity, R.color.sp_primary_blue);
                    EdgeToEdge.enable((ComponentActivity) activity,
                            SystemBarStyle.dark(azul),
                            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
                }
            }

            @Override
            public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                ViewGroup contenido = activity.findViewById(android.R.id.content);
                if (contenido == null || contenido.getChildCount() == 0) return;

                View raiz = contenido.getChildAt(0);
                // Las pantallas que ya manejan sus insets (fitsSystemWindows) no se tocan
                if (raiz.getFitsSystemWindows()) return;

                ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
                    Insets barras = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    v.setPadding(v.getPaddingLeft(), barras.top, v.getPaddingRight(), v.getPaddingBottom());
                    return insets;
                });
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }
            @Override public void onActivityStarted(@NonNull Activity activity) { }
            @Override public void onActivityResumed(@NonNull Activity activity) { }
            @Override public void onActivityPaused(@NonNull Activity activity) { }
            @Override public void onActivityStopped(@NonNull Activity activity) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
            @Override public void onActivityDestroyed(@NonNull Activity activity) { }
        });
    }
}
