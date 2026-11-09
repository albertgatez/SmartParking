package com.smartparking.ui.notifications;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;
import com.smartparking.notificaciones.Notificacion;
import com.smartparking.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

/** Lista de avisos de la pestaña Avisos. */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.AvisoViewHolder> {

    public interface OnQuitarListener {
        void onQuitar(Notificacion notificacion);
    }

    private List<Notificacion> lista = new ArrayList<>();
    private final OnQuitarListener listener;

    public NotificationsAdapter(OnQuitarListener listener) {
        this.listener = listener;
    }

    public void actualizar(List<Notificacion> nueva) {
        this.lista = nueva != null ? nueva : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** El aviso en esa posición (para deslizar a la derecha), o null. */
    public Notificacion getItem(int position) {
        return position >= 0 && position < lista.size() ? lista.get(position) : null;
    }

    @NonNull
    @Override
    public AvisoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notificacion, parent, false);
        return new AvisoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AvisoViewHolder holder, int position) {
        Notificacion n = lista.get(position);

        holder.tvTitulo.setText(n.getTitulo());
        holder.tvMensaje.setText(n.getMensaje());
        // Misma conversión UTC -> hora local que usa el Historial de pagos
        holder.tvFecha.setText(DateTimeUtils.formatearFechaHoraLocal(n.getCreatedAt()));

        ImageViewCompat.setImageTintList(holder.ivIcono,
                ColorStateList.valueOf(colorPorTipo(holder.itemView.getContext(), n.getTipo())));

        holder.btnQuitar.setOnClickListener(v -> listener.onQuitar(n));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    /** Verde: pagos; naranja: tiempo por terminar; rojo: vehículo en el cajón; azul: lo demás. */
    private static int colorPorTipo(Context context, String tipo) {
        if (tipo == null) return ContextCompat.getColor(context, R.color.sp_primary_blue);
        switch (tipo) {
            case "reserva_confirmada":
            case "reserva_extendida":
                return Color.parseColor("#4CAF50");
            case "recordatorio_fin":
                return Color.parseColor("#FF9800");
            case "cajon_ocupado":
                return Color.parseColor("#F44336");
            default:
                return ContextCompat.getColor(context, R.color.sp_primary_blue);
        }
    }

    static class AvisoViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcono;
        final TextView tvTitulo, tvMensaje, tvFecha;
        final ImageButton btnQuitar;

        AvisoViewHolder(@NonNull View itemView) {
            super(itemView);
            btnQuitar = itemView.findViewById(R.id.btnQuitarAviso);
            ivIcono = itemView.findViewById(R.id.ivIconoAviso);
            tvTitulo = itemView.findViewById(R.id.tvTituloAviso);
            tvMensaje = itemView.findViewById(R.id.tvMensajeAviso);
            tvFecha = itemView.findViewById(R.id.tvFechaAviso);
        }
    }
}