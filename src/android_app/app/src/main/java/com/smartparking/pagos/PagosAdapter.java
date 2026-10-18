package com.smartparking.pagos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smartparking.utils.DateTimeUtils;
import com.smartparking.R;

import java.util.List;
import java.util.Locale;

public class PagosAdapter extends RecyclerView.Adapter<PagosAdapter.PagoViewHolder> {

    private List<Pago> listaPagos;

    public PagosAdapter(List<Pago> listaPagos) {
        this.listaPagos = listaPagos;
    }

    @NonNull
    @Override
    public PagoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pago, parent, false);
        return new PagoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PagoViewHolder holder, int position) {
        Pago pago = listaPagos.get(position);

        holder.txtIdReserva.setText("Reserva: " + pago.getId_reserva());
        holder.txtMonto.setText(String.format(Locale.getDefault(), "$%.2f", pago.getMonto_total()));
        holder.txtFecha.setText(DateTimeUtils.formatearFechaHoraLocal(pago.getFecha_pago()));
    }

    @Override
    public int getItemCount() {
        return listaPagos.size();
    }

    public static class PagoViewHolder extends RecyclerView.ViewHolder {
        TextView txtIdReserva, txtMonto, txtFecha;

        public PagoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtIdReserva = itemView.findViewById(R.id.txtIdReserva);
            txtMonto = itemView.findViewById(R.id.txtMonto);
            txtFecha = itemView.findViewById(R.id.txtFecha);
        }
    }
}

