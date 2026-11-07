package com.smartparking.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

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

        adapter = new NotificationsAdapter();
        binding.rvNotificaciones.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvNotificaciones.setAdapter(adapter);

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