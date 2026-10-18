package com.smartparking.estacionamientos;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.estacionamientos.CajonConHorarios.HorarioEstado;
import com.smartparking.R;

import java.util.List;

public class HorariosAdapter extends RecyclerView.Adapter<HorariosAdapter.HorarioViewHolder> {

    private static final int COLOR_DISPONIBLE = Color.parseColor("#B3E5FC");
    private static final int COLOR_OCUPADO = Color.parseColor("#E0E0E0");
    private static final int COLOR_PASADO = Color.parseColor("#9E9E9E");
    private static final int COLOR_SELECCIONADO = Color.parseColor("#0288D1");
    private static final int COLOR_RESTRINGIDO = Color.parseColor("#FFCDD2"); // Rojo claro para indicar restricción

    private List<HorarioEstado> horarios;
    private OnHorarioClickListener listener;

    public interface OnHorarioClickListener {
        void onHorarioClick(int position, HorarioEstado horario);
    }

    public HorariosAdapter(List<HorarioEstado> horarios, OnHorarioClickListener listener) {
        this.horarios = horarios;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HorarioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_horario, parent, false);
        return new HorarioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HorarioViewHolder holder, int position) {
        HorarioEstado horario = horarios.get(position);

        holder.tvHora.setText(horario.getHorarioFormateado());

        int color;
        boolean clickable;

        if (horario.isSeleccionado()) {
            color = COLOR_SELECCIONADO;
            holder.tvHora.setTextColor(Color.WHITE);
            clickable = true;
        } else {
            switch (horario.getEstado()) {
                case DISPONIBLE:
                    color = COLOR_DISPONIBLE;
                    holder.tvHora.setTextColor(Color.BLACK);
                    clickable = true;
                    break;
                case OCUPADO:
                    color = COLOR_OCUPADO;
                    holder.tvHora.setTextColor(Color.GRAY);
                    clickable = false;
                    break;
                case PASADO:
                    color = COLOR_PASADO;
                    holder.tvHora.setTextColor(Color.WHITE);
                    clickable = false;
                    break;
                case RESTRINGIDO:
                    color = COLOR_RESTRINGIDO;
                    holder.tvHora.setTextColor(Color.parseColor("#B71C1C")); // Rojo oscuro
                    clickable = false;
                    break;
                default:
                    color = COLOR_DISPONIBLE;
                    holder.tvHora.setTextColor(Color.BLACK);
                    clickable = true;
            }
        }

        holder.cardView.setCardBackgroundColor(color);
        holder.cardView.setClickable(clickable);
        holder.cardView.setEnabled(clickable);
        holder.cardView.setAlpha(clickable ? 1.0f : 0.5f);

        if (clickable) {
            holder.cardView.setOnClickListener(v -> {
                horario.setSeleccionado(!horario.isSeleccionado());
                notifyItemChanged(position);

                if (listener != null) {
                    listener.onHorarioClick(position, horario);
                }
            });
        } else {
            holder.cardView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return horarios.size();
    }

    static class HorarioViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvHora;

        public HorarioViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            tvHora = itemView.findViewById(R.id.tvHora);
        }
    }
}