package com.jazaldcorp.inveragrovendedores.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Transparencia para el vendedor: muestra exactamente la misma ruta que ve su supervisor en
 * el mapa en vivo de Odoo. Nada oculto — si la empresa rastrea su ubicación, él también la ve.
 */
public class MiRutaFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private TextView tvInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mi_ruta, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvInfo = view.findViewById(R.id.tvMiRutaInfo);
        view.findViewById(R.id.btnBackMiRuta).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapaMiRuta);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        cargarMiRuta();
    }

    @SuppressWarnings("unchecked")
    private void cargarMiRuta() {
        SessionManager session = new SessionManager(requireContext());
        String token = session.getAuthToken();
        if (token == null || token.isEmpty()) return;

        if (!session.isLocationTrackingEnabled()) {
            tvInfo.setText("Tu empresa no tiene activado el seguimiento de ubicación en vivo.");
            return;
        }

        ApiService apiService = ApiClient.getService(requireContext());
        apiService.getMiRutaHoy("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;
                Object pointsObj = result.get("points");
                if (!(pointsObj instanceof List)) return;

                List<Map<String, Object>> puntos = (List<Map<String, Object>>) pointsObj;
                if (puntos.isEmpty()) {
                    tvInfo.setText("Aún no se registró ningún punto de tu ruta hoy.");
                    return;
                }

                PolylineOptions linea = new PolylineOptions().color(0xFF1976D2).width(8f);
                LatLng ultimo = null;
                for (Map<String, Object> p : puntos) {
                    Object latObj = p.get("latitude");
                    Object lngObj = p.get("longitude");
                    if (!(latObj instanceof Number) || !(lngObj instanceof Number)) continue;
                    ultimo = new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
                    linea.add(ultimo);
                }
                mMap.addPolyline(linea);
                if (ultimo != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ultimo, 14f));
                }
                tvInfo.setText(puntos.size() + " punto(s) registrados hoy. Esto es exactamente lo que ve tu supervisor.");
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "Sin conexión para cargar tu ruta.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
