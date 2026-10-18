package com.smartparking.estacionamientos;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.estacionamientos.CajonConHorarios.HorarioEstado;
import com.smartparking.R;

import java.util.List;

public class CajonesAdapter extends RecyclerView.Adapter<CajonesAdapter.CajonViewHolder> {

    private List<CajonConHorarios> cajonesConHorarios;
    private OnHorarioSeleccionadoListener listener;
    private int cajonSeleccionadoActual = -1;

    public interface OnHorarioSeleccionadoListener {
        void onHorarioSeleccionado(int cajonPosition, int horarioPosition, boolean seleccionado);
    }

    public CajonesAdapter(List<CajonConHorarios> cajonesConHorarios, OnHorarioSeleccionadoListener listener) {
        this.cajonesConHorarios = cajonesConHorarios;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CajonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cajon_horarios, parent, false);
        return new CajonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CajonViewHolder holder, int position) {
        CajonConHorarios cajonConHorarios = cajonesConHorarios.get(position);
        Cajon cajon = cajonConHorarios.getCajon();

        // Mostrar identificador del cajón
        String identificador = cajon.getIdentificadorCompleto();

        // Si es cajón para discapacitados, agregar indicador
        if (cajon.isDiscapacitado()) {
            identificador = "♿ " + identificador;
        }

        holder.tvCajonId.setText(identificador);

        // Cambiar estilo si el cajón no es accesible para el usuario
        if (!cajonConHorarios.isAccesibleParaUsuario()) {
            holder.tvCajonId.setTextColor(Color.parseColor("#B71C1C")); // Rojo oscuro
            holder.itemView.setAlpha(0.7f);
        } else {
            holder.tvCajonId.setTextColor(Color.BLACK);
            holder.itemView.setAlpha(1.0f);
        }

        // Configurar RecyclerView de horarios
        HorariosAdapter horariosAdapter = new HorariosAdapter(
                cajonConHorarios.getHorarios(),
                new HorariosAdapter.OnHorarioClickListener() {
                    @Override
                    public void onHorarioClick(int horarioPosition, HorarioEstado horario) {
                        int cajonPos = holder.getAdapterPosition();
                        if (horario.isSeleccionado()) {
                            if (cajonSeleccionadoActual != -1 && cajonSeleccionadoActual != cajonPos) {
                                Toast.makeText(holder.itemView.getContext(),
                                        "Solo puedes seleccionar un espacio de estacionamiento a la vez.",
                                        Toast.LENGTH_LONG).show();
                                horario.setSeleccionado(false);
                                if (holder.recyclerViewHorarios.getAdapter() != null) {
                                    holder.recyclerViewHorarios.getAdapter().notifyItemChanged(horarioPosition);
                                }
                                return;
                            }
                        }
                        if (horario.isSeleccionado()) {
                            cajonSeleccionadoActual = cajonPos;
                        } else {
                            boolean haySeleccion = false;
                            for (HorarioEstado h : cajonConHorarios.getHorarios()) {
                                if (h.isSeleccionado()) {
                                    haySeleccion = true;
                                    break;
                                }
                            }
                            if (!haySeleccion) {
                                cajonSeleccionadoActual = -1;
                            }
                        }

                        if (listener != null) {
                            listener.onHorarioSeleccionado(cajonPos, horarioPosition, horario.isSeleccionado());
                        }
                    }
                }
        );

        holder.recyclerViewHorarios.setLayoutManager(
                new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        holder.recyclerViewHorarios.setAdapter(horariosAdapter);
    }

    @Override
    public int getItemCount() {
        return cajonesConHorarios.size();
    }

    private void limpiarSeleccionesCajon(int cajonPosition) {
        if (cajonPosition >= 0 && cajonPosition < cajonesConHorarios.size()) {
            for (HorarioEstado horario : cajonesConHorarios.get(cajonPosition).getHorarios()) {
                horario.setSeleccionado(false);
            }
        }
    }

    static class CajonViewHolder extends RecyclerView.ViewHolder {
        TextView tvCajonId;
        RecyclerView recyclerViewHorarios;

        public CajonViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCajonId = itemView.findViewById(R.id.tvCajonId);
            recyclerViewHorarios = itemView.findViewById(R.id.recyclerViewHorarios);
        }
    }
}