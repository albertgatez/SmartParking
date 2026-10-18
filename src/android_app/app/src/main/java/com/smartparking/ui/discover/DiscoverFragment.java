package com.smartparking.ui.discover;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoverFragment extends Fragment implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "DiscoverFragment";

    private GoogleMap mMap;
    private DiscoverViewModel viewModel;
    private FusedLocationProviderClient fusedLocationClient;
    private Location userLocation;
    private Map<Marker, Estacionamiento> markerEstacionamientoMap = new HashMap<>();
    private EstacionamientoBottomSheet bottomSheet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar ViewModel
        viewModel = new ViewModelProvider(this).get(DiscoverViewModel.class);

        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Inicializar mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Observar cambios en los datos
        setupObservers();
    }

    private void setupObservers() {
        viewModel.getEstacionamientos().observe(getViewLifecycleOwner(), this::mostrarEstacionamientosEnMapa);

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            // Aquí podrías mostrar/ocultar un ProgressBar si lo deseas
            Log.d(TAG, "Loading: " + isLoading);
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Configurar el mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Solicitar permisos de ubicación
        if (checkLocationPermission()) {
            enableMyLocation();
        } else {
            requestLocationPermission();
        }

        // Configurar click en marcadores
        mMap.setOnMarkerClickListener(marker -> {
            Estacionamiento estacionamiento = markerEstacionamientoMap.get(marker);
            if (estacionamiento != null) {
                mostrarDetallesEstacionamiento(estacionamiento);
            }
            return true;
        });

        // Cargar estacionamientos
        viewModel.cargarEstacionamientos();
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void enableMyLocation() {
        if (checkLocationPermission()) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                obtenerUbicacionActual();
            }
        }
    }

    private void obtenerUbicacionActual() {
        if (checkLocationPermission()) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            userLocation = location;
                            LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 13f));
                            Log.d(TAG, "Ubicación del usuario obtenida: " + userLatLng);
                        } else {
                            Log.w(TAG, "No se pudo obtener la ubicación del usuario");
                            // Posición por defecto (puedes cambiarla a Guadalajara, por ejemplo)
                            LatLng defaultLocation = new LatLng(20.6597, -103.3496);
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f));
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al obtener ubicación", e);
                        Toast.makeText(requireContext(), "Error al obtener ubicación", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private BitmapDescriptor getBitmapFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        if (vectorDrawable == null) {
            Log.e("DiscoverFragment", "Vector drawable not found");
            return null;
        }

        vectorDrawable.setBounds(
                0,
                0,
                vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight()
        );

        Bitmap bitmap = Bitmap.createBitmap(
                vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void mostrarEstacionamientosEnMapa(List<Estacionamiento> estacionamientos) {
        if (mMap == null || estacionamientos == null) return;

        // Limpiar marcadores previos
        mMap.clear();
        markerEstacionamientoMap.clear();

        // Agregar marcadores para cada estacionamiento
        for (Estacionamiento estacionamiento : estacionamientos) {
            LatLng position = new LatLng(estacionamiento.getLatitud(), estacionamiento.getLongitud());

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(estacionamiento.getNombre())
                    .icon(getBitmapFromVector(getContext(), R.drawable.ic_parking_circle)));

            if (marker != null) {
                markerEstacionamientoMap.put(marker, estacionamiento);
            }
        }

        Log.d(TAG, "Marcadores agregados: " + estacionamientos.size());
    }

    private void mostrarDetallesEstacionamiento(Estacionamiento estacionamiento) {
        double distancia = calcularDistancia(estacionamiento);

        bottomSheet = new EstacionamientoBottomSheet(estacionamiento, distancia);
        bottomSheet.show(getParentFragmentManager(), "EstacionamientoBottomSheet");
    }

    private double calcularDistancia(Estacionamiento estacionamiento) {
        if (userLocation == null) {
            return 0.0;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                userLocation.getLatitude(),
                userLocation.getLongitude(),
                estacionamiento.getLatitud(),
                estacionamiento.getLongitud(),
                results
        );

        // Convertir de metros a kilómetros
        return results[0] / 1000.0;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }
}