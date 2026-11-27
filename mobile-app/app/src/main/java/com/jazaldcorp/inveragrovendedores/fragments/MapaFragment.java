package com.jazaldcorp.inveragrovendedores.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import com.jazaldcorp.inveragrovendedores.repositories.ClienteRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapaFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ClienteRepository clienteRepository;
    private AppDatabase db;
    private List<Cliente> listaClientes;
    private TextView tvMapaTitulo, tvMapaResumen;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mapa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = AppDatabase.getInstance(requireContext());
        clienteRepository = new ClienteRepository(requireContext());

        tvMapaTitulo = view.findViewById(R.id.tvMapaTitulo);
        tvMapaResumen = view.findViewById(R.id.tvMapaResumen);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapaGoogle);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        clienteRepository.getClientesLocales().observe(getViewLifecycleOwner(), clientes -> {
            if (clientes != null) {
                listaClientes = clientes;
                dibujarClientesEnMapa();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        dibujarClientesEnMapa();
    }

    private void dibujarClientesEnMapa() {
        if (mMap == null || listaClientes == null || listaClientes.isEmpty()) {
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // Ahora la lista de IDs visitados devuelve enteros (List<Integer>)
            List<Integer> idsVisitados = db.clienteDao().getIdsVisitados();
            int paradasHoy = db.rutaParadaDao().getTotalParadas();

            requireActivity().runOnUiThread(() -> {
                int visitados = 0;
                for (Cliente c : listaClientes) {
                    if (idsVisitados.contains(c.id)) visitados++;
                }
                int pendientes = listaClientes.size() - visitados;

                if (paradasHoy > 0) {
                    tvMapaTitulo.setText("Ruta de Hoy");
                    tvMapaResumen.setText(String.format(java.util.Locale.getDefault(),
                            "%d parada(s) planificadas · %d pendiente(s) · %d visitada(s)", paradasHoy, pendientes, visitados));
                } else {
                    tvMapaTitulo.setText("Mapa de Clientes");
                    tvMapaResumen.setText(String.format(java.util.Locale.getDefault(),
                            "%d cliente(s) · %d pendiente(s) · %d visitado(s)", listaClientes.size(), pendientes, visitados));
                }

                mMap.clear();
                LatLng ultimaCoordenada = null;

                for (Cliente cliente : listaClientes) {
                    if (cliente.latitud == 0.0 && cliente.longitud == 0.0) continue;

                    LatLng posicionCliente = new LatLng(cliente.latitud, cliente.longitud);
                    boolean yaVisitado = idsVisitados.contains(cliente.id);

                    mMap.addMarker(new MarkerOptions()
                                    .position(posicionCliente)
                                    .title(cliente.nombre_fundo)
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                            yaVisitado ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_RED
                                    )))
                            .setTag(cliente);

                    ultimaCoordenada = posicionCliente;
                }

                mMap.setOnMarkerClickListener(marker -> {
                    Cliente clienteSeleccionado = (Cliente) marker.getTag();
                    if (clienteSeleccionado != null) {
                        mostrarBottomSheet(clienteSeleccionado, idsVisitados.contains(clienteSeleccionado.id));
                    }
                    return false;
                });

                if (ultimaCoordenada != null) {
                    LatLng coordenadaFinal = ultimaCoordenada;
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(coordenadaFinal, 14f));
                }
            });
        });
    }

    private void mostrarBottomSheet(Cliente cliente, boolean esVisitado) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(R.layout.layout_bottom_sheet);

        TextView tvNombre = dialog.findViewById(R.id.tvBsNombre);
        TextView tvContacto = dialog.findViewById(R.id.tvBsContacto);
        TextView tvEstado = dialog.findViewById(R.id.tvBsEstado);
        LinearLayout btnRuta = dialog.findViewById(R.id.btnBsVerRuta);
        LinearLayout btnDetalles = dialog.findViewById(R.id.btnBsVerDetalles);
        ImageView closeBottomSheet = dialog.findViewById(R.id.closeBottomSheet);

        tvNombre.setText(cliente.nombre_fundo);
        tvContacto.setText("Contacto: " + cliente.nombre_contacto + " | " + cliente.telefono);
        tvEstado.setText(esVisitado ? "VISITADO" : "PENDIENTE");
        tvEstado.setTextColor(esVisitado ?
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark) :
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));

        btnRuta.setOnClickListener(v -> {
            dialog.dismiss();
            abrirGoogleMaps(cliente.latitud, cliente.longitud);
        });

        btnDetalles.setOnClickListener(v -> {
            dialog.dismiss();
            abrirDetallesCliente(cliente.id); // Pasa el ID como int
        });

        closeBottomSheet.setOnClickListener(view -> dialog.dismiss());

        dialog.show();
    }

    private void abrirGoogleMaps(double lat, double lng) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lng);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(requireContext(), "Google Maps no está instalado", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirDetallesCliente(int clienteId) { // Cambiado a int para Odoo
        DetalleClienteFragment detalleFragment = new DetalleClienteFragment();

        Bundle args = new Bundle();
        args.putInt("CLIENTE_ID", clienteId); // Cambiado a putInt
        detalleFragment.setArguments(args);

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, detalleFragment)
                .addToBackStack(null)
                .commit();
    }
}