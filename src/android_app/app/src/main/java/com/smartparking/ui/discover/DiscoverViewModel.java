package com.smartparking.ui.discover;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.LatLng;
import com.smartparking.estacionamientos.Estacionamiento;
import com.smartparking.network.SupabaseClient;

import java.util.List;

public class DiscoverViewModel extends ViewModel {

    private final MutableLiveData<List<Estacionamiento>> estacionamientos = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final SupabaseClient supabaseClient;

    public DiscoverViewModel() {
        supabaseClient = new SupabaseClient();
        isLoading.setValue(false);
    }

    public LiveData<List<Estacionamiento>> getEstacionamientos() {
        return estacionamientos;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void cargarEstacionamientos() {
        isLoading.setValue(true);

        supabaseClient.obtenerEstacionamientos(new SupabaseClient.SupabaseCallbackEstacionamientos() {
            @Override
            public void onSuccess(List<Estacionamiento> listaEstacionamientos) {
                isLoading.setValue(false);
                estacionamientos.setValue(listaEstacionamientos);
            }

            @Override
            public void onError(String message) {
                isLoading.setValue(false);
                error.setValue(message);
            }
        });
    }


    // LatLng de la cámara (centro de la vista)
    public final MutableLiveData<LatLng> cameraPosition = new MutableLiveData<>();

    // Zoom actual del mapa (float)
    public final MutableLiveData<Float> zoomLevel = new MutableLiveData<>();

    // Flag para saber si ya se guardó alguna posición (evita sobrescribir con nulos)
    public final MutableLiveData<Boolean> hasPosition = new MutableLiveData<>(false);

    public void saveCameraState(LatLng latLng, float zoom) {
        cameraPosition.postValue(latLng);
        zoomLevel.postValue(zoom);
        hasPosition.postValue(true);
    }
}