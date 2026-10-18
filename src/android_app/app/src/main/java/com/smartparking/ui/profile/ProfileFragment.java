package com.smartparking.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.smartparking.perfil.EditarPerfil;
import com.smartparking.pagos.HistorialActivity;
import com.smartparking.databinding.FragmentProfileBinding;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String idUsuario;

    // Launcher para recibir resultado de EditarPerfil
    private ActivityResultLauncher<Intent> editarPerfilLauncher;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Registrar el launcher ANTES de onCreateView
        editarPerfilLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d("ProfileFragment", "Volviendo de EditarPerfil, recargando datos...");
                        recargarDatos();
                    }
                }
        );

    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Obtener datos del usuario
        idUsuario = viewModel.obtenerIdDelStorage(requireActivity());
        String nombre = viewModel.obtenerNombreDelStorage(requireActivity());

        // Mostrar nombre
        binding.txtNombre.setText("Buen día, " + nombre);

        // Cargar imagen de perfil en el ImageView dentro del CardView
        if (idUsuario != null && !idUsuario.isEmpty()) {
            viewModel.cargarImagenPerfil(requireActivity(), idUsuario, binding.marcoFotoPerfilPerfil);
        }

        // Configurar listeners
        configurarListeners();

        return root;
    }

    private void configurarListeners() {

        binding.btnEditarPerfil.setOnClickListener(v -> irAEditarPerfil());
        binding.btnHistorial.setOnClickListener(v -> irAHistorial());
        binding.btnMetodosPago.setOnClickListener(v -> irAMetodosPago());
        binding.btnMetodosPago2.setOnClickListener(v -> viewModel.cerrarSesion(requireActivity()));
    }

    private void irAEditarPerfil() {
        Intent intent = new Intent(requireActivity(), EditarPerfil.class);
        intent.putExtra("id", idUsuario);
        editarPerfilLauncher.launch(intent);
    }

    private void recargarDatos() {
        // Recargar nombre desde SharedPreferences
        String nombre = viewModel.obtenerNombreDelStorage(requireActivity());
        binding.txtNombre.setText("Buen día, " + nombre);

        // Recargar imagen desde la BD
        if (idUsuario != null && !idUsuario.isEmpty()) {
            Log.d("ProfileFragment", "Recargando imagen para usuario: " + idUsuario);
            viewModel.cargarImagenPerfil(requireActivity(), idUsuario, binding.marcoFotoPerfilPerfil);
        }
    }

    private void irAHistorial() {
        Intent intent = new Intent(requireActivity(), HistorialActivity.class);
        startActivity(intent);
    }

    private void irAMetodosPago() {
        Toast.makeText(requireActivity(), "Métodos de Pago (próximamente)", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("ProfileFragment", "onResume llamado");
        // Recargar datos cada vez que el fragment se hace visible
        recargarDatos();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}