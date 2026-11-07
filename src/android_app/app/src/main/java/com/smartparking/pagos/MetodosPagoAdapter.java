package com.smartparking.pagos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;

import java.util.List;

public class MetodosPagoAdapter extends RecyclerView.Adapter<MetodosPagoAdapter.MetodoViewHolder> {

    public interface OnEliminarListener {
        void onEliminar(MetodoPago metodo);
    }

    private final List<MetodoPago> listaMetodos;
    private final OnEliminarListener listener;
    private boolean habilitado = true;

    public MetodosPagoAdapter(List<MetodoPago> listaMetodos, OnEliminarListener listener) {
        this.listaMetodos = listaMetodos;
        this.listener = listener;
    }

    /** Desactiva los botones de eliminar mientras hay una operación en curso. */
    public void setHabilitado(boolean habilitado) {
        if (this.habilitado == habilitado) return;
        this.habilitado = habilitado;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MetodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_metodo_pago, parent, false);
        return new MetodoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MetodoViewHolder holder, int position) {
        MetodoPago metodo = listaMetodos.get(position);

        holder.txtMarca.setText(metodo.getMarcaVisible());
        holder.txtNumero.setText("•••• " + metodo.getUltimos4());
        holder.txtVencimiento.setText("Vence " + metodo.getVencimiento());

        holder.btnEliminar.setEnabled(habilitado);
        holder.btnEliminar.setAlpha(habilitado ? 1f : 0.4f);
        holder.btnEliminar.setContentDescription("Eliminar tarjeta terminación " + metodo.getUltimos4());
        holder.btnEliminar.setOnClickListener(v -> listener.onEliminar(metodo));
    }

    @Override
    public int getItemCount() {
        return listaMetodos.size();
    }

    public static class MetodoViewHolder extends RecyclerView.ViewHolder {
        TextView txtMarca, txtNumero, txtVencimiento;
        ImageButton btnEliminar;

        public MetodoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtMarca = itemView.findViewById(R.id.txtMarca);
            txtNumero = itemView.findViewById(R.id.txtNumero);
            txtVencimiento = itemView.findViewById(R.id.txtVencimiento);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }
    }
}
