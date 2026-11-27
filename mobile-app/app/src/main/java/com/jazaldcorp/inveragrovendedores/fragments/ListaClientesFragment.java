package com.jazaldcorp.inveragrovendedores.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.adapters.ClienteAdapter;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import com.jazaldcorp.inveragrovendedores.repositories.ClienteRepository;
import com.jazaldcorp.inveragrovendedores.utils.BrandingHelper;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListaClientesFragment extends Fragment {

    private static final String TAG = "ListaClientesDebug";
    private static final int FILTRO_TODOS = 0;
    private static final int FILTRO_PENDIENTES = 1;
    private static final int FILTRO_COMPLETADOS = 2;

    private RecyclerView recyclerView;
    private ClienteAdapter adapter;
    private ClienteRepository clienteRepository;
    private AppDatabase db;
    private FusedLocationProviderClient fusedLocationClient;

    private TextInputEditText etBuscar;
    private MaterialButton btnFiltroTodos, btnFiltroPendientes, btnFiltroCompletados;
    private TextView tvVacio;

    private List<Cliente> clientesCompletos = new ArrayList<>();
    private List<Integer> idsVisitadosCompleto = new ArrayList<>();
    private int filtroActual = FILTRO_TODOS;
    private String textoBusqueda = "";
    private String colorMarca;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lista_clientes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = AppDatabase.getInstance(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        colorMarca = new SessionManager(requireContext()).getBrandingColor();

        recyclerView = view.findViewById(R.id.recyclerViewClientes);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ClienteAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setOnClienteClickListener(this::abrirDetalleCliente);

        etBuscar = view.findViewById(R.id.etBuscarCliente);
        btnFiltroTodos = view.findViewById(R.id.btnFiltroTodos);
        btnFiltroPendientes = view.findViewById(R.id.btnFiltroPendientes);
        btnFiltroCompletados = view.findViewById(R.id.btnFiltroCompletados);
        tvVacio = view.findViewById(R.id.tvClientesVacio);

        btnFiltroTodos.setOnClickListener(v -> seleccionarFiltro(FILTRO_TODOS));
        btnFiltroPendientes.setOnClickListener(v -> seleccionarFiltro(FILTRO_PENDIENTES));
        btnFiltroCompletados.setOnClickListener(v -> seleccionarFiltro(FILTRO_COMPLETADOS));
        actualizarEstiloFiltros();

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString();
                aplicarFiltrosYMostrar();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        clienteRepository = new ClienteRepository(requireContext());

        // Escuchamos los clientes locales desde Room/LiveData
        clienteRepository.getClientesLocales().observe(getViewLifecycleOwner(), clientes -> {
            if (clientes != null) {
                Log.d(TAG, "LiveData emitido. Cantidad de clientes locales en Room: " + clientes.size());
            } else {
                Log.d(TAG, "LiveData emitido, pero la lista de clientes es NULL");
            }

            if (clientes != null && !clientes.isEmpty()) {
                cargarIdsVisitadosYFiltrar(clientes);
            } else {
                Log.w(TAG, "La tabla de clientes en Room está VACÍA. Mostrando adapter vacío.");
                clientesCompletos = new ArrayList<>();
                idsVisitadosCompleto = new ArrayList<>();
                aplicarFiltrosYMostrar();
            }
        });
    }

    private void seleccionarFiltro(int filtro) {
        filtroActual = filtro;
        actualizarEstiloFiltros();
        aplicarFiltrosYMostrar();
    }

    private void actualizarEstiloFiltros() {
        aplicarEstiloBoton(btnFiltroTodos, filtroActual == FILTRO_TODOS);
        aplicarEstiloBoton(btnFiltroPendientes, filtroActual == FILTRO_PENDIENTES);
        aplicarEstiloBoton(btnFiltroCompletados, filtroActual == FILTRO_COMPLETADOS);
    }

    private void aplicarEstiloBoton(MaterialButton boton, boolean seleccionado) {
        if (seleccionado) {
            Integer color = BrandingHelper.parseColor(colorMarca);
            boton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color != null ? color : 0xFF252525));
            boton.setTextColor(0xFFFFFFFF);
        } else {
            boton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEEEEEE));
            boton.setTextColor(0xFF757575);
        }
    }

    private void cargarIdsVisitadosYFiltrar(List<Cliente> clientes) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Integer> idsVisitados = db.clienteDao().getIdsVisitados();

            requireActivity().runOnUiThread(() -> {
                clientesCompletos = clientes;
                idsVisitadosCompleto = idsVisitados != null ? idsVisitados : new ArrayList<>();
                aplicarFiltrosYMostrar();
            });
        });
    }

    private void aplicarFiltrosYMostrar() {
        List<Cliente> filtrados = new ArrayList<>();
        String busquedaLower = textoBusqueda.toLowerCase(Locale.getDefault()).trim();

        for (Cliente c : clientesCompletos) {
            boolean visitado = idsVisitadosCompleto.contains(c.id);

            if (filtroActual == FILTRO_PENDIENTES && visitado) continue;
            if (filtroActual == FILTRO_COMPLETADOS && !visitado) continue;

            if (!busquedaLower.isEmpty()) {
                String nombreFundo = c.nombre_fundo != null ? c.nombre_fundo.toLowerCase(Locale.getDefault()) : "";
                String nombreContacto = c.nombre_contacto != null ? c.nombre_contacto.toLowerCase(Locale.getDefault()) : "";
                if (!nombreFundo.contains(busquedaLower) && !nombreContacto.contains(busquedaLower)) continue;
            }

            filtrados.add(c);
        }

        adapter.setClientes(filtrados, idsVisitadosCompleto);
        tvVacio.setVisibility(filtrados.isEmpty() ? View.VISIBLE : View.GONE);
        calcularDistanciasSiEsPosible(filtrados);
    }

    private void calcularDistanciasSiEsPosible(List<Cliente> clientes) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null && adapter != null) {
                    Log.d(TAG, "Ubicación obtenida para calcular distancias: Lat=" + location.getLatitude() + ", Lng=" + location.getLongitude());
                    for (int i = 0; i < clientes.size(); i++) {
                        adapter.calcularDistancia(i, location.getLatitude(), location.getLongitude());
                    }
                } else {
                    Log.w(TAG, "No se pudo obtener la última ubicación GPS (location es null).");
                }
            });
        } else {
            Log.w(TAG, "No hay permisos de ubicación concedidos para calcular distancias.");
        }
    }

    private void abrirDetalleCliente(Cliente cliente) {
        DetalleClienteFragment detalleFragment = new DetalleClienteFragment();
        Bundle args = new Bundle();
        args.putInt("CLIENTE_ID", cliente.id);
        detalleFragment.setArguments(args);

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, detalleFragment)
                .addToBackStack(null)
                .commit();
    }
}
