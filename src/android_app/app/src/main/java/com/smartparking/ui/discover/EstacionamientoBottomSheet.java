package com.smartparking.ui.discover;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.R;
import com.smartparking.reservas.ReservaRepository;
import com.smartparking.reservas.SeleccionReservaActivity;
import com.smartparking.network.SupabaseClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EstacionamientoBottomSheet extends BottomSheetDialogFragment {

    private Estacionamiento estacionamiento;
    private double distancia;
    private RecyclerView recyclerViewImagenes;
    private ImagenesAdapter imagenesAdapter;
    private ProgressBar progressBar;
    private TextView tvNombre, tvPrecio, tvDistancia;
    private Button btnSeleccionar;

    private static final String ARG_ESTACIONAMIENTO = "estacionamiento";
    private static final String ARG_DISTANCIA = "distancia";

    // Android necesita un constructor vacío para recrear el fragment (p. ej. al girar la pantalla)
    public EstacionamientoBottomSheet() {}

    public EstacionamientoBottomSheet(Estacionamiento estacionamiento, double distancia) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_ESTACIONAMIENTO, estacionamiento);
        args.putDouble(ARG_DISTANCIA, distancia);
        setArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            estacionamiento = (Estacionamiento) getArguments().getSerializable(ARG_ESTACIONAMIENTO);
            distancia = getArguments().getDouble(ARG_DISTANCIA);
        }
        if (estacionamiento == null) {
            dismissAllowingStateLoss();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_estacionamiento, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (estacionamiento == null) return;

        // Inicializar vistas
        recyclerViewImagenes = view.findViewById(R.id.recyclerViewImagenes);
        progressBar = view.findViewById(R.id.progressBar);
        tvNombre = view.findViewById(R.id.tvNombreEstacionamiento);
        tvPrecio = view.findViewById(R.id.tvPrecio);
        tvDistancia = view.findViewById(R.id.tvDistancia);
        btnSeleccionar = view.findViewById(R.id.btnSeleccionar);

        // Configurar RecyclerView horizontal
        recyclerViewImagenes.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        imagenesAdapter = new ImagenesAdapter(new ArrayList<>());
        recyclerViewImagenes.setAdapter(imagenesAdapter);

        // Mostrar información básica
        tvNombre.setText(estacionamiento.getNombre());
        tvPrecio.setText(String.format(Locale.getDefault(), "$%.2f por hora", estacionamiento.getPrecioHora()));
        tvDistancia.setText(String.format(Locale.getDefault(), "%.2f km", distancia));

        // Cargar imágenes
        cargarImagenes();

        // Configurar botón Seleccionar
        btnSeleccionar.setOnClickListener(v -> {
            // Solo se permite una reserva a la vez (igual que en Inicio)
            ReservaRepository repo = ReservaRepository.getInstance(requireContext());
            if (repo.tieneReservaActiva()) {
                String msg = repo.reservaEsPendiente()
                        ? "Ya tienes una reserva pendiente de pago"
                        : "Ya tienes una reserva activa";
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                return;
            }

            // Navegar a la activity de cajones y horarios
            Intent intent = SeleccionReservaActivity.createIntent(getContext(), estacionamiento);
            startActivity(intent);
            dismiss(); // Cerrar el BottomSheet
        });
    }

    private void cargarImagenes() {
        progressBar.setVisibility(View.VISIBLE);

        SupabaseClient supabaseClient = new SupabaseClient();
        supabaseClient.obtenerImagenesEstacionamiento(estacionamiento.getId(), new SupabaseClient.SupabaseImagenesCallback() {
            @Override
            public void onSuccess(List<String> imagenesUrls) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);

                if (imagenesUrls.isEmpty()) {
                    // Si no hay imágenes, mostrar una imagen por defecto
                    imagenesUrls.add("https://via.placeholder.com/400x200?text=Sin+imagen");
                }

                imagenesAdapter.actualizarImagenes(imagenesUrls);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Error al cargar imágenes: " + message, Toast.LENGTH_SHORT).show();

                // Mostrar imagen por defecto en caso de error
                List<String> defaultImage = new ArrayList<>();
                defaultImage.add("https://via.placeholder.com/400x200?text=Error+al+cargar");
                imagenesAdapter.actualizarImagenes(defaultImage);
            }
        });
    }
}