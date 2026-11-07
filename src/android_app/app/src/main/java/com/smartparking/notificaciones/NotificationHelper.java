package com.smartparking.notificaciones;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.smartparking.R;
import com.smartparking.ui.MainActivity;

/**
 * Crea el canal de notificaciones y muestra las notificaciones de la app.
 * El id del canal debe coincidir con "default_notification_channel_id" del Manifest.
 */
public final class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    public static final String CANAL_RESERVAS = "reservas";

    private NotificationHelper() {}

    /** Se llama una vez desde SmartParkingApp (minSdk 27, los canales siempre existen). */
    public static void crearCanales(Context context) {
        NotificationChannel canal = new NotificationChannel(
                CANAL_RESERVAS, "Reservas y avisos", NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription("Bienvenida, recordatorios y avisos de tus reservas");

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(canal);
    }

    /** Muestra una notificación; al tocarla abre la app. Sin permiso (Android 13+) no hace nada. */
    @SuppressLint("MissingPermission") // el permiso se revisa justo abajo
    public static void mostrar(Context context, String titulo, String mensaje) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Sin permiso POST_NOTIFICATIONS; no se muestra la notificación");
            return;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notificacion = new NotificationCompat.Builder(context, CANAL_RESERVAS)
                .setSmallIcon(R.drawable.ic_stat_sp)
                .setColor(ContextCompat.getColor(context, R.color.sp_primary_blue))
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), notificacion);
    }
}