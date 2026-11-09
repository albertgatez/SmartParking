package com.smartparking.pagos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.R;

import java.util.List;

public class MetodosPagoAdapter extends RecyclerView.Adapter<MetodosPagoAdapter.MetodoViewHolder> {

    public interface Listener {
        void onEliminar(MetodoPago metodo);
        void onPredeterminar(MetodoPago metodo);
        /** Solo en modo selección: se tocó la tarjeta. */
        void onSeleccionar(MetodoPago metodo);
    }

    private final List<MetodoPago> listaMetodos;
    private final Listener listener;
    private boolean habilitado = true;
    private boolean modoSeleccion = false;
    private String idSeleccionado = null;

    /** En modo selección cada tarjeta muestra un botón de opción y se selecciona al tocarla. */
    public void setModoSeleccion(boolean modoSeleccion) {
        this.modoSeleccion = modoSeleccion;
        notifyDataSetChanged();
    }

    public void setIdSeleccionado(String idSeleccionado) {
        this.idSeleccionado = idSeleccionado;
        notifyDataSetChanged();
    }

    public MetodosPagoAdapter(List<MetodoPago> listaMetodos, Listener listener) {
        this.listaMetodos = listaMetodos;
        this.listener = listener;
    }

    /** Desactiva los botones mientras hay una operación en curso. */
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

        // Etiqueta "Predeterminada" o botón para elegirla
        holder.txtPredeterminada.setVisibility(metodo.isPredeterminada() ? View.VISIBLE : View.GONE);
        holder.btnPredeterminar.setVisibility(metodo.isPredeterminada() ? View.GONE : View.VISIBLE);
        holder.btnPredeterminar.setEnabled(habilitado);
        holder.btnPredeterminar.setAlpha(habilitado ? 1f : 0.4f);
        holder.btnPredeterminar.setOnClickListener(v -> listener.onPredeterminar(metodo));

        // Modo selección: botón de opción y toque en toda la tarjeta
        holder.radioSeleccion.setVisibility(modoSeleccion ? View.VISIBLE : View.GONE);
        holder.radioSeleccion.setChecked(modoSeleccion && metodo.getId().equals(idSeleccionado));
        if (modoSeleccion) {
            holder.filaMetodo.setOnClickListener(v -> {
                if (habilitado) listener.onSeleccionar(metodo);
            });
        } else {
            holder.filaMetodo.setOnClickListener(null);
            holder.filaMetodo.setClickable(false);
        }

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
        TextView txtMarca, txtNumero, txtVencimiento, txtPredeterminada, btnPredeterminar;
        ImageButton btnEliminar;
        RadioButton radioSeleccion;
        View filaMetodo;

        public MetodoViewHolder(@NonNull View itemView) {
            super(itemView);
            radioSeleccion = itemView.findViewById(R.id.radioSeleccion);
            filaMetodo = itemView.findViewById(R.id.filaMetodo);
            txtMarca = itemView.findViewById(R.id.txtMarca);
            txtNumero = itemView.findViewById(R.id.txtNumero);
            txtVencimiento = itemView.findViewById(R.id.txtVencimiento);
            txtPredeterminada = itemView.findViewById(R.id.txtPredeterminada);
            btnPredeterminar = itemView.findViewById(R.id.btnPredeterminar);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }
    }
}
