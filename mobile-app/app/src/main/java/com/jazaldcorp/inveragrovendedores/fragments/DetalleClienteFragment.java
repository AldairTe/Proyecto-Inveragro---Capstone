package com.jazaldcorp.inveragrovendedores.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.adapters.VisitaAdapter;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import com.jazaldcorp.inveragrovendedores.models.Visita;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetalleClienteFragment extends Fragment {

    private int clienteId; // Cambiado a int para coincidir con Odoo
    private Cliente clienteActual;

    private TextView tvNombre, tvTelefono, tvDireccion, tvDistanciaInfo,tvIdDetalle;
    private MaterialCardView cardUbicacion;
    private ImageView btnBack;
    private MaterialButton btnIniciarVisita;
    private ImageView btnRecalcularDistancia;
    private RecyclerView rvVisitas;
    private VisitaAdapter visitaAdapter;
    private TextView tvSinVisitas;
    private TextView tvResumenPedidos;
    AppDatabase db;

    private FusedLocationProviderClient fusedLocationClient;

    // Radio máximo permitido en metros para considerar que el vendedor llegó
    private static final float RADIO_PERMITIDO_METROS = 50.0f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detalle_cliente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Recuperamos el ID como int (con valor por defecto -1 por seguridad)
        if (getArguments() != null) {
            clienteId = getArguments().getInt("CLIENTE_ID", -1);
        }

        enlazarVistas(view);

        db = AppDatabase.getInstance(requireContext());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        rvVisitas.setLayoutManager(new LinearLayoutManager(requireContext()));
        visitaAdapter = new VisitaAdapter();
        rvVisitas.setAdapter(visitaAdapter);

        if (clienteId != -1) {
            cargarDatosCliente();
            cargarHistorialVisitas(clienteId);
            cargarResumenPedidos(clienteId);
        } else {
            Toast.makeText(getContext(), "Error: ID de cliente inválido", Toast.LENGTH_SHORT).show();
        }

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        btnRecalcularDistancia.setOnClickListener(v -> {
            tvDistanciaInfo.setText("Recalculando ubicación...");
            tvDistanciaInfo.setTextColor(android.graphics.Color.parseColor("#F57C00"));
            calcularDistanciaGeocerca();
        });

        btnIniciarVisita.setOnClickListener(v -> {
            // Saltamos al formulario de registro pasando el ID numérico del cliente
            RegistroVisitaFragment registroFragment = new RegistroVisitaFragment();

            Bundle args = new Bundle();
            args.putInt("CLIENTE_ID", clienteId);
            registroFragment.setArguments(args);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, registroFragment)
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void enlazarVistas(View view) {
        tvNombre = view.findViewById(R.id.tvNombreDetalle);
        tvTelefono = view.findViewById(R.id.tvTelefonoDetalle);
        tvDireccion = view.findViewById(R.id.tvDireccionDetalle);
        tvIdDetalle=view.findViewById(R.id.tvIdDetalle);
        tvDistanciaInfo = view.findViewById(R.id.tvDistanciaInfo);
        cardUbicacion = view.findViewById(R.id.cardUbicacion);
        btnBack = view.findViewById(R.id.btnBack);
        btnIniciarVisita = view.findViewById(R.id.btnIniciarVisita);
        btnRecalcularDistancia = view.findViewById(R.id.btnRecalcularDistancia);
        rvVisitas = view.findViewById(R.id.rvVisitas);
        tvSinVisitas = view.findViewById(R.id.tvSinVisitas);
        tvResumenPedidos = view.findViewById(R.id.tvResumenPedidos);
    }

    private void cargarDatosCliente() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            clienteActual = db.clienteDao().getClienteById(clienteId);

            requireActivity().runOnUiThread(() -> {
                if (clienteActual != null) {
                    tvNombre.setText(clienteActual.nombre_fundo);
                    tvTelefono.setText(clienteActual.telefono);
                    tvIdDetalle.setText(clienteActual.nombre_contacto);
                    tvDireccion.setText(new StringBuilder(String.valueOf(clienteActual.latitud)).append(",").append(clienteActual.longitud));

                    calcularDistanciaGeocerca();
                }
            });
        });
    }

    @SuppressLint("MissingPermission")
    private void calcularDistanciaGeocerca() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tvDistanciaInfo.setText("⚠️ Falta permiso de GPS. Ve a Resumen y presiona Marcar Asistencia primero.");
            tvDistanciaInfo.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(requireActivity(), locationGpsVendedor -> {
                    if (locationGpsVendedor != null && clienteActual != null) {

                        Location locationCliente = new Location("Destino");
                        locationCliente.setLatitude(clienteActual.latitud);
                        locationCliente.setLongitude(clienteActual.longitud);

                        float distanciaMetros = locationGpsVendedor.distanceTo(locationCliente);
                        actualizarInterfazDistancia(distanciaMetros);

                    } else {
                        tvDistanciaInfo.setText("Enciende el GPS del celular y espera un momento.");
                        tvDistanciaInfo.setTextColor(android.graphics.Color.parseColor("#F57C00"));
                    }
                })
                .addOnFailureListener(requireActivity(), e -> {
                    tvDistanciaInfo.setText("Error al leer el sensor: " + e.getMessage());
                    tvDistanciaInfo.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                });
    }

    private void actualizarInterfazDistancia(float distancia) {
        int distanciaRedondeada = Math.round(distancia);

        if (distancia <= RADIO_PERMITIDO_METROS) {
            tvDistanciaInfo.setText("Estás a " + distanciaRedondeada + " metros del cliente");
            tvDistanciaInfo.setTextColor(android.graphics.Color.parseColor("#388E3C"));
            cardUbicacion.setCardBackgroundColor(android.graphics.Color.parseColor("#F1F8E9"));
            btnIniciarVisita.setEnabled(true);
        } else {
            tvDistanciaInfo.setText("Estás a " + distanciaRedondeada + " metros. Acércate para visitar.");
            tvDistanciaInfo.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
            cardUbicacion.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"));
            btnIniciarVisita.setEnabled(false);
        }
    }

    private void cargarHistorialVisitas(int clienteId) {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("SessionPrefs", android.content.Context.MODE_PRIVATE);
        String token = prefs.getString("auth_token", null);

        if (token == null || token.isEmpty()) {
            // Fallback a Room si no hay internet o token
            cargarVisitasDesdeRoom(clienteId);
            return;
        }

        // Preparamos la estructura JSON-RPC que espera Odoo
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("customer_id", clienteId);

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("params", params);

        com.jazaldcorp.inveragrovendedores.api.ApiService apiService = com.jazaldcorp.inveragrovendedores.api.ApiClient.getService(requireContext());

        apiService.getVisitas("Bearer " + token, body).enqueue(new retrofit2.Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<java.util.Map<String, Object>> call, @NonNull retrofit2.Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    java.util.Map<String, Object> resultado = response.body();

                    // Odoo con type='json' encapsula la respuesta dentro de la llave "result"
                    java.util.Map<String, Object> resultData = (java.util.Map<String, Object>) resultado.get("result");

                    if (resultData != null && resultData.containsKey("visits")) {
                        @SuppressWarnings("unchecked")
                        List<java.util.Map<String, Object>> listaMaps = (List<java.util.Map<String, Object>>) resultData.get("visits");

                        List<Visita> historialRemoto = new java.util.ArrayList<>();
                        if (listaMaps != null) {
                            for (java.util.Map<String, Object> map : listaMaps) {
                                Visita v = new Visita();

                                v.external_uuid = map.get("external_uuid") != null ? String.valueOf(map.get("external_uuid")) : "";
                                v.customer_id = clienteId;
                                v.date_visit = map.get("date_visit") != null ? String.valueOf(map.get("date_visit")) : "";

                                Object latObj = map.get("latitude");
                                v.latitude = latObj instanceof Number ? ((Number) latObj).doubleValue() : 0.0;

                                Object lngObj = map.get("longitude");
                                v.longitude = lngObj instanceof Number ? ((Number) lngObj).doubleValue() : 0.0;

                                v.notes = map.get("notes") != null ? String.valueOf(map.get("notes")) : "";
                                v.state = map.get("state") != null ? String.valueOf(map.get("state")) : "";
                                v.sincronizado = true;

                                historialRemoto.add(v);
                            }
                        }

                        requireActivity().runOnUiThread(() -> actualizarAdapterVisitas(historialRemoto));
                        return;
                    }
                }
                // Si la API falla, intentamos cargar desde Room como respaldo offline
                cargarVisitasDesdeRoom(clienteId);
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<java.util.Map<String, Object>> call, @NonNull Throwable t) {
                // Si no hay red, cargamos desde la base de datos local Room
                cargarVisitasDesdeRoom(clienteId);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void cargarResumenPedidos(int clienteId) {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("SessionPrefs", android.content.Context.MODE_PRIVATE);
        String token = prefs.getString("auth_token", null);

        if (token == null || token.isEmpty()) {
            cargarResumenPedidosDesdeRoom(clienteId);
            return;
        }

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("customer_id", clienteId);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("params", params);

        com.jazaldcorp.inveragrovendedores.api.ApiService apiService = com.jazaldcorp.inveragrovendedores.api.ApiClient.getService(requireContext());
        apiService.getPedidosRemotos("Bearer " + token, body).enqueue(new retrofit2.Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<java.util.Map<String, Object>> call, @NonNull retrofit2.Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object resultObj = response.body().get("result");
                    if (resultObj instanceof java.util.Map) {
                        Object ordersObj = ((java.util.Map<String, Object>) resultObj).get("orders");
                        if (ordersObj instanceof List) {
                            List<java.util.Map<String, Object>> orders = (List<java.util.Map<String, Object>>) ordersObj;
                            int cantidad = orders.size();
                            double total = 0;
                            String ultimaFecha = null;
                            for (java.util.Map<String, Object> o : orders) {
                                Object montoObj = o.get("amount_total");
                                if (montoObj instanceof Number) total += ((Number) montoObj).doubleValue();
                                if (ultimaFecha == null && o.get("date_order") != null) {
                                    ultimaFecha = String.valueOf(o.get("date_order"));
                                }
                            }
                            mostrarResumenPedidos(cantidad, total, ultimaFecha);
                            return;
                        }
                    }
                }
                cargarResumenPedidosDesdeRoom(clienteId);
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<java.util.Map<String, Object>> call, @NonNull Throwable t) {
                cargarResumenPedidosDesdeRoom(clienteId);
            }
        });
    }

    private void cargarResumenPedidosDesdeRoom(int clienteId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<com.jazaldcorp.inveragrovendedores.models.Pedido> pedidos = db.pedidoDao().getPedidosPorCliente(clienteId);
            int cantidad = pedidos != null ? pedidos.size() : 0;
            double total = 0;
            String ultimaFecha = null;
            if (pedidos != null) {
                for (com.jazaldcorp.inveragrovendedores.models.Pedido p : pedidos) {
                    total += p.montoTotal;
                    if (ultimaFecha == null) ultimaFecha = p.date_order;
                }
            }
            int cantidadFinal = cantidad;
            double totalFinal = total;
            String fechaFinal = ultimaFecha;
            requireActivity().runOnUiThread(() -> mostrarResumenPedidos(cantidadFinal, totalFinal, fechaFinal));
        });
    }

    private void mostrarResumenPedidos(int cantidad, double total, String ultimaFecha) {
        if (!isAdded()) return;
        if (cantidad == 0) {
            tvResumenPedidos.setText("Aún no hay pedidos registrados para este cliente.");
            return;
        }
        String textoFecha = ultimaFecha != null ? " · Último: " + ultimaFecha.split(" ")[0] : "";
        tvResumenPedidos.setText(String.format(java.util.Locale.getDefault(),
                "%d pedido(s) · Total histórico: S/ %.2f%s", cantidad, total, textoFecha));
    }

    private void cargarVisitasDesdeRoom(int clienteId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Visita> historialLocal = db.visitaDao().getVisitasPorCliente(clienteId);
            requireActivity().runOnUiThread(() -> actualizarAdapterVisitas(historialLocal));
        });
    }

    private void actualizarAdapterVisitas(List<Visita> lista) {
        if (lista != null && !lista.isEmpty()) {
            visitaAdapter.setVisitas(lista);
            rvVisitas.setVisibility(View.VISIBLE);
            tvSinVisitas.setVisibility(View.GONE);
        } else {
            rvVisitas.setVisibility(View.GONE);
            tvSinVisitas.setVisibility(View.VISIBLE);
        }
    }
}