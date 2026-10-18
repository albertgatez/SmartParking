package com.smartparking.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.R;
import com.smartparking.network.SupabaseClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter para mostrar la lista de estacionamientos en HomeFragment
 */
public class EstacionamientosHomeAdapter extends RecyclerView.Adapter<EstacionamientosHomeAdapter.EstacionamientoViewHolder> {

    private List<Estacionamiento> estacionamientos;
    private OnEstacionamientoClickListener listener;
    private SupabaseClient supabaseClient;
    private Map<Integer, List<String>> imagenesCache; // Cache para listas de imágenes

    public interface OnEstacionamientoClickListener {
        void onEstacionamientoClick(Estacionamiento estacionamiento);
    }

    public EstacionamientosHomeAdapter(List<Estacionamiento> estacionamientos, OnEstacionamientoClickListener listener) {
        this.estacionamientos = estacionamientos;
        this.listener = listener;
        this.supabaseClient = new SupabaseClient();
        this.imagenesCache = new HashMap<>();
    }

    @NonNull
    @Override
    public EstacionamientoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_estacionamiento_home, parent, false);
        return new EstacionamientoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EstacionamientoViewHolder holder, int position) {
        Estacionamiento estacionamiento = estacionamientos.get(position);

        holder.tvNombre.setText(estacionamiento.getNombre());
        holder.tvPrecio.setText(String.format(Locale.getDefault(), "$%.2f/hr", estacionamiento.getPrecioHora()));

        // Configura el RecyclerView horizontal para el carrusel
        setupCarruselImagenes(holder, estacionamiento, position);

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEstacionamientoClick(estacionamiento);
            }
        });
    }

    private void setupCarruselImagenes(EstacionamientoViewHolder holder, Estacionamiento estacionamiento, int position) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                holder.itemView.getContext(),
                LinearLayoutManager.HORIZONTAL,
                false
        );
        holder.recyclerViewImagenes.setLayoutManager(layoutManager);

        // Inicializa el adapter del carrusel
        CarruselImagenesAdapter carruselAdapter = new CarruselImagenesAdapter(new ArrayList<>());
        holder.recyclerViewImagenes.setAdapter(carruselAdapter);

        // Carga las imágenes para el carrusel
        cargarImagenesParaCarrusel(estacionamiento, carruselAdapter, holder.progressBar, position);
    }

    private void cargarImagenesParaCarrusel(Estacionamiento estacionamiento,
                                            CarruselImagenesAdapter adapter,
                                            ProgressBar progressBar,
                                            int position) {

        // Verifica si ya tenemos las imágenes en cache
        if (imagenesCache.containsKey(estacionamiento.getId())) {
            List<String> imagenesUrls = imagenesCache.get(estacionamiento.getId());
            adapter.actualizarImagenes(imagenesUrls);
            progressBar.setVisibility(View.GONE);
            return;
        }

        // Muestra ProgressBar mientras se cargan las imágenes
        progressBar.setVisibility(View.VISIBLE);

        supabaseClient.obtenerImagenesEstacionamiento(estacionamiento.getId(), new SupabaseClient.SupabaseImagenesCallback() {
            @Override
            public void onSuccess(List<String> imagenesUrls) {
                progressBar.setVisibility(View.GONE);

                if (imagenesUrls.isEmpty()) {
                    // Si no hay imágenes, muestra una imagen por defecto
                    imagenesUrls.add("https://via.placeholder.com/400x200?text=Sin+imagen");
                }

                // Guardar en cache
                imagenesCache.put(estacionamiento.getId(), imagenesUrls);

                // Actualizar el adapter del carrusel
                if (position < estacionamientos.size() && estacionamientos.get(position).getId() == estacionamiento.getId()) {
                    adapter.actualizarImagenes(imagenesUrls);
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);

                // En caso de error, muestra una imagen de error
                List<String> defaultImage = new ArrayList<>();
                defaultImage.add("https://via.placeholder.com/400x200?text=Error+al+cargar");

                imagenesCache.put(estacionamiento.getId(), defaultImage);

                if (position < estacionamientos.size() && estacionamientos.get(position).getId() == estacionamiento.getId()) {
                    adapter.actualizarImagenes(defaultImage);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return estacionamientos.size();
    }

    public void actualizarEstacionamientos(List<Estacionamiento> nuevosEstacionamientos) {
        this.estacionamientos = nuevosEstacionamientos;
        this.imagenesCache.clear();
        notifyDataSetChanged();
    }

    static class EstacionamientoViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        RecyclerView recyclerViewImagenes;
        ProgressBar progressBar;
        TextView tvNombre;
        TextView tvPrecio;

        public EstacionamientoViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            recyclerViewImagenes = itemView.findViewById(R.id.recyclerViewImagenesHome);
            progressBar = itemView.findViewById(R.id.progressBarHome);
            tvNombre = itemView.findViewById(R.id.tvNombreEstacionamientoHome);
            tvPrecio = itemView.findViewById(R.id.tvPrecioEstacionamientoHome);
        }
    }

    // Adapter interno para el carrusel de imágenes
    public static class CarruselImagenesAdapter extends RecyclerView.Adapter<CarruselImagenesAdapter.ImagenViewHolder> {

        private List<String> imagenesUrls;

        public CarruselImagenesAdapter(List<String> imagenesUrls) {
            this.imagenesUrls = imagenesUrls;
        }

        @NonNull
        @Override
        public ImagenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_imagen_carrusel, parent, false);
            return new ImagenViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImagenViewHolder holder, int position) {
            String imagenUrl = imagenesUrls.get(position);
            Glide.with(holder.itemView.getContext())
                    .load(imagenUrl)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_placeholder_image)
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return imagenesUrls.size();
        }

        public void actualizarImagenes(List<String> nuevasImagenes) {
            this.imagenesUrls = nuevasImagenes;
            notifyDataSetChanged();
        }

        static class ImagenViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            public ImagenViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageViewCarrusel);
            }
        }
    }
}