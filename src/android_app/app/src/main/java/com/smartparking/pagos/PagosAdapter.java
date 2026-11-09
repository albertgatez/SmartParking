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
import java.util.Map;

public class PagosAdapter extends RecyclerView.Adapter<PagosAdapter.PagoViewHolder> {

    private final List<Pago> listaPagos;
    private final Map<String, String> nombresEstacionamiento;

    /**
     * @param nombresEstacionamiento id del estacionamiento (como texto) -> nombre
     */
    public PagosAdapter(List<Pago> listaPagos, Map<String, String> nombresEstacionamiento) {
        this.listaPagos = listaPagos;
        this.nombresEstacionamiento = nombresEstacionamiento;
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

        String nombre = nombresEstacionamiento.get(pago.getId_estacionamiento());
        holder.txtEstacionamiento.setText(nombre != null ? nombre : "Estacionamiento " + pago.getId_estacionamiento());

        holder.txtDetalle.setText(etiquetaTipo(pago.getTipo()) + " · Reserva #" + pago.getId_reserva());
        holder.txtFecha.setText(DateTimeUtils.formatearFechaHoraLocal(pago.getFecha_pago()));
        holder.txtMonto.setText(formatear(pago.getMonto_total()));

        if (pago.getSubtotal() > 0) {
            holder.txtDesglose.setText(String.format(Locale.getDefault(),
                    "Subtotal %s · Comisión %s · Descuento -%s",
                    formatear(pago.getSubtotal()),
                    formatear(pago.getComision()),
                    formatear(pago.getDescuento())));
            holder.txtDesglose.setVisibility(View.VISIBLE);
        } else {
            holder.txtDesglose.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return listaPagos.size();
    }

    private static String etiquetaTipo(String tipo) {
        if ("extension".equals(tipo)) return "Extensión de tiempo";
        if ("reserva".equals(tipo)) return "Reserva";
        return "Pago";
    }

    private static String formatear(double monto) {
        return String.format(Locale.getDefault(), "$%.2f", monto);
    }

    public static class PagoViewHolder extends RecyclerView.ViewHolder {
        TextView txtEstacionamiento, txtDetalle, txtFecha, txtMonto, txtDesglose;

        public PagoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtEstacionamiento = itemView.findViewById(R.id.txtEstacionamiento);
            txtDetalle = itemView.findViewById(R.id.txtDetalle);
            txtFecha = itemView.findViewById(R.id.txtFecha);
            txtMonto = itemView.findViewById(R.id.txtMonto);
            txtDesglose = itemView.findViewById(R.id.txtDesglose);
        }
    }
}
