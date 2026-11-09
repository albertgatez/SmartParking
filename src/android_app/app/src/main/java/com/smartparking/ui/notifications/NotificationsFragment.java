package com.smartparking.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.smartparking.databinding.FragmentNotificationsBinding;
import com.smartparking.notificaciones.Notificacion;

import java.util.List;

/** Pestaña Avisos: historial de notificaciones del usuario. */
public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private NotificationsViewModel viewModel;
    private NotificationsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // La X de cada aviso lo quita
        adapter = new NotificationsAdapter(this::quitarAviso);
        binding.rvNotificaciones.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvNotificaciones.setAdapter(adapter);

        // Deslizar un aviso a la derecha también lo quita
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder destino) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direccion) {
                Notificacion aviso = adapter.getItem(vh.getBindingAdapterPosition());
                if (aviso != null) {
                    quitarAviso(aviso);
                } else {
                    adapter.notifyDataSetChanged();
                }
            }
        }).attachToRecyclerView(binding.rvNotificaciones);

        viewModel.getNotificaciones().observe(getViewLifecycleOwner(), lista -> {
            adapter.actualizar(lista);
            actualizarEstado();
        });
        viewModel.getCargando().observe(getViewLifecycleOwner(), cargando -> actualizarEstado());
        viewModel.getError().observe(getViewLifecycleOwner(), mensaje -> actualizarEstado());
    }

    /** Se recarga cada vez que la pestaña se hace visible, así aparecen los avisos nuevos. */
    @Override
    public void onResume() {
        super.onResume();
        viewModel.cargar();
    }

    /** Quita el aviso y ofrece deshacer durante unos segundos. */
    private void quitarAviso(Notificacion aviso) {
        int posicion = viewModel.ocultar(aviso, this::mostrarErrorAviso);
        if (posicion < 0 || binding == null) return;

        Snackbar.make(binding.getRoot(), "Aviso eliminado", Snackbar.LENGTH_LONG)
                .setAction("Deshacer", v -> viewModel.restaurar(aviso, posicion, this::mostrarErrorAviso))
                .show();
    }

    private void mostrarErrorAviso(String mensaje) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show();
    }

    /** Decide qué se ve: la lista, el indicador de carga o el mensaje de vacío / error. */
    private void actualizarEstado() {
        if (binding == null) return;

        List<Notificacion> lista = viewModel.getNotificaciones().getValue();
        boolean hayAvisos = lista != null && !lista.isEmpty();
        boolean cargando = Boolean.TRUE.equals(viewModel.getCargando().getValue());
        String error = viewModel.getError().getValue();

        // Con avisos ya en pantalla, las recargas son silenciosas
        binding.progressBar.setVisibility(!hayAvisos && cargando ? View.VISIBLE : View.GONE);
        binding.tvVacio.setVisibility(!hayAvisos && !cargando ? View.VISIBLE : View.GONE);
        binding.tvVacio.setText(error != null ? error : "No tienes avisos todavía");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}