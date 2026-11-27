package com.jazaldcorp.inveragrovendedores.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.activities.LoginActivity;
import com.jazaldcorp.inveragrovendedores.activities.MainActivity;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Asistencia;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import com.jazaldcorp.inveragrovendedores.models.RutaParada;
import com.jazaldcorp.inveragrovendedores.models.Usuario;
import com.jazaldcorp.inveragrovendedores.repositories.ClienteRepository;
import com.jazaldcorp.inveragrovendedores.repositories.RouteRepository;
import com.jazaldcorp.inveragrovendedores.repositories.SincronizacionRepository;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private TextView tvSaludoUsuario, tvProgresoClientes, tvPendientesCount, tvReprogramadosCount, tvProximoClienteNombre, tvProximoClienteDireccion, tvVerEnMapa, tvEstadoLicencia;
    private LinearProgressIndicator progressBarClientes;
    private MaterialButton btnMarcarAsistencia, btnVerMapaGlobal, btnFinalizarJornada;

    private AppDatabase db;
    private Usuario usuarioActual;
    private SessionManager session;

    private FusedLocationProviderClient fusedLocationClient;

    // Lanzador para permisos de GPS
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    obtenerUbicacionYGuardar();
                } else {
                    Toast.makeText(getContext(), "El GPS es obligatorio para marcar asistencia", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // Enlace de vistas
        tvSaludoUsuario = root.findViewById(R.id.tvSaludoUsuario);
        tvProgresoClientes = root.findViewById(R.id.tvProgresoClientes);
        tvPendientesCount = root.findViewById(R.id.tvPendientesCount);
        tvReprogramadosCount = root.findViewById(R.id.tvReprogramadosCount);
        tvProximoClienteNombre = root.findViewById(R.id.tvProximoClienteNombre);
        tvProximoClienteDireccion = root.findViewById(R.id.tvProximoClienteDireccion);
        tvVerEnMapa = root.findViewById(R.id.tvVerEnMapa);
        tvEstadoLicencia = root.findViewById(R.id.tvEstadoLicencia);
        progressBarClientes = root.findViewById(R.id.progressBarClientes);

        btnMarcarAsistencia = root.findViewById(R.id.btnMarcarAsistencia);
        btnVerMapaGlobal = root.findViewById(R.id.btnVerMapaGlobal);
        btnFinalizarJornada = root.findViewById(R.id.btnFinalizarJornada);

        db = AppDatabase.getInstance(getContext());
        session = new SessionManager(getContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getContext());

        // Aplica el color de marca configurado en Odoo (queda el genérico si no hay ninguno cacheado)
        String colorMarca = session.getBrandingColor();
        Integer colorParseado = com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.parseColor(colorMarca);
        if (colorParseado != null) {
            progressBarClientes.setIndicatorColor(colorParseado);
            com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarColorFondo(btnVerMapaGlobal, colorMarca);
        }

        // Refresca en segundo plano la configuración de asistencia (habilitada/deshabilitada,
        // marcaciones permitidas por día). Cada empresa cliente la define en su propio Odoo.
        sincronizarConfigAsistencia();

        // Cargar datos locales del vendedor
        cargarDatosUsuario();

        // Actualizamos los números del tablero
        cargarResumenRuta();

        // Inicialización del repositorio local y motor offline
        ClienteRepository repositorio = new ClienteRepository(requireContext());
        repositorio.actualizarClientesDesdeServidor();

        SincronizacionRepository syncRepo = new SincronizacionRepository(requireContext());
        syncRepo.ejecutarSincronizacionCompleta();

        // Ruta planificada de hoy (si la empresa la usa); al terminar, refresca el resumen
        // por si cambia el total/pendientes respecto a la lista completa de clientes.
        new RouteRepository(requireContext()).actualizarRutaDeHoy(() -> {
            if (isAdded()) requireActivity().runOnUiThread(this::cargarResumenRuta);
        });

        // Control y acciones del botón de Asistencia
        btnMarcarAsistencia.setOnClickListener(v -> {
            if (!isHoraAutomaticaActivada()) {
                Toast.makeText(getContext(), "⚠️ Seguridad: Debes configurar la fecha y hora del sistema en 'Automático' para poder marcar asistencia.", Toast.LENGTH_LONG).show();
                return;
            }
            verificarPermisosGps();
        });

        // Transición directa al Fragment del Mapa
        btnVerMapaGlobal.setOnClickListener(v -> irAlMapaConValidacion());
        tvVerEnMapa.setOnClickListener(v -> irAlMapaConValidacion());

        btnFinalizarJornada.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).finalizarJornadaUbicacion();
            }
            Toast.makeText(getContext(), "Dejaste de compartir tu ubicación. Vuelve a marcar asistencia mañana para reactivarla.", Toast.LENGTH_LONG).show();
            btnFinalizarJornada.setVisibility(View.GONE);
        });
        actualizarVisibilidadBotonFinalizarJornada();

        actualizarEstadoSesion();

        return root;
    }

    /**
     * Muestra "Finalizar Jornada" solo si la empresa usa ubicación en vivo Y ya se inició el
     * rastreo hoy (asistencia marcada, o arranque automático cuando la empresa no usa asistencia).
     */
    private void actualizarVisibilidadBotonFinalizarJornada() {
        boolean mostrar = session.isLocationTrackingEnabled()
                && (getActivity() instanceof MainActivity)
                && ((MainActivity) getActivity()).asistenciaRegistradaHoy;
        btnFinalizarJornada.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    private void irAlMapaConValidacion() {
        MainActivity mainActivity = (MainActivity) getActivity();

        if (mainActivity != null && !mainActivity.asistenciaRegistradaHoy) {
            mainActivity.mostrarAlertaAsistencia();
            return;
        }

        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new MapaFragment())
                .addToBackStack(null)
                .commit();
    }

    /**
     * El texto que antes decía "Verificando licencia..." (sin ninguna lógica detrás) ahora
     * muestra hasta cuándo es válida la sesión (expira junto con el token de Odoo).
     */
    private void actualizarEstadoSesion() {
        String expiresAt = session.getTokenExpiresAt();
        if (expiresAt == null || expiresAt.isEmpty()) {
            tvEstadoLicencia.setText("✅ Dispositivo activado");
            return;
        }
        try {
            java.text.SimpleDateFormat sdfOdoo = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date fecha = sdfOdoo.parse(expiresAt);
            java.text.SimpleDateFormat sdfLegible = new java.text.SimpleDateFormat("d 'de' MMMM, yyyy", new java.util.Locale("es", "ES"));
            tvEstadoLicencia.setText("✅ Sesión válida hasta el " + sdfLegible.format(fecha));
        } catch (Exception e) {
            tvEstadoLicencia.setText("✅ Dispositivo activado");
        }
    }

    private void cargarDatosUsuario() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            usuarioActual = db.usuarioDao().getUsuarioLogueado();

            if (usuarioActual != null) {
                int asistenciasHoy = contarAsistenciasHoy(usuarioActual.id);

                requireActivity().runOnUiThread(() -> {
                    tvSaludoUsuario.setText(usuarioActual.nombre_completo);
                    actualizarEstadoBotonAsistencia(asistenciasHoy);
                });
            } else {
                new SessionManager(getContext()).logout();
                startActivity(new Intent(getContext(), LoginActivity.class));
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });
    }

    private int contarAsistenciasHoy(int usuarioId) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long inicioDeHoy = cal.getTimeInMillis();
        return db.asistenciaDao().verificarAsistenciaHoy(usuarioId, inicioDeHoy);
    }

    /**
     * Aplica al botón "Marcar Asistencia" la configuración que define cada empresa cliente
     * desde su Odoo: oculto si la empresa no usa asistencia, o bloqueado una vez alcanzado
     * el máximo de marcaciones diarias (0 = sin límite, puede marcar varias veces al día).
     */
    private void actualizarEstadoBotonAsistencia(int asistenciasHoy) {
        if (!session.isAttendanceEnabled()) {
            btnMarcarAsistencia.setVisibility(View.GONE);
            return;
        }
        btnMarcarAsistencia.setVisibility(View.VISIBLE);

        int maxPorDia = session.getAttendanceMaxPerDay();
        boolean limiteAlcanzado = maxPorDia > 0 && asistenciasHoy >= maxPorDia;

        if (limiteAlcanzado) {
            btnMarcarAsistencia.setText(asistenciasHoy > 1 ? "Asistencia Registrada (" + asistenciasHoy + ")" : "Asistencia Registrada");
            btnMarcarAsistencia.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            btnMarcarAsistencia.setTextColor(android.graphics.Color.WHITE);
            btnMarcarAsistencia.setClickable(false);
            btnMarcarAsistencia.setFocusable(false);
        } else {
            btnMarcarAsistencia.setText(asistenciasHoy > 0 ? "Marcar Otra Asistencia" : "Marcar Asistencia");
            Integer colorMarca = com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.parseColor(session.getBrandingColor());
            btnMarcarAsistencia.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorMarca != null ? colorMarca : android.graphics.Color.parseColor("#252525")));
            btnMarcarAsistencia.setTextColor(android.graphics.Color.WHITE);
            btnMarcarAsistencia.setEnabled(true);
            btnMarcarAsistencia.setClickable(true);
            btnMarcarAsistencia.setFocusable(true);
        }
    }

    private void sincronizarConfigAsistencia() {
        String token = session.getAuthToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = ApiClient.getService(requireContext());
        Map<String, Object> bodyJsonRpc = new HashMap<>();
        bodyJsonRpc.put("jsonrpc", "2.0");
        bodyJsonRpc.put("method", "call");
        bodyJsonRpc.put("params", new HashMap<>());
        bodyJsonRpc.put("id", null);

        apiService.getAttendanceConfig("Bearer " + token, bodyJsonRpc).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resultObj;
                if (result.get("error") != null) return;

                boolean enabled = !Boolean.FALSE.equals(result.get("enabled"));
                Object maxObj = result.get("max_per_day");
                int maxPerDay = maxObj instanceof Number ? ((Number) maxObj).intValue() : 1;

                session.saveAttendanceConfig(enabled, maxPerDay);

                if (!enabled && session.isLocationTrackingEnabled() && getActivity() instanceof MainActivity) {
                    // Esta empresa no usa asistencia, pero sí ubicación en vivo: no hay un
                    // gesto de "marcar asistencia" del que colgarnos, así que arrancamos el
                    // rastreo directo al entrar (sigue pidiendo permisos la primera vez).
                    ((MainActivity) getActivity()).iniciarJornadaUbicacion();
                    actualizarVisibilidadBotonFinalizarJornada();
                }

                if (usuarioActual != null && isAdded()) {
                    // Room no permite leer en el hilo principal; el callback de Retrofit sí corre ahí.
                    Executors.newSingleThreadExecutor().execute(() -> {
                        int asistenciasHoy = contarAsistenciasHoy(usuarioActual.id);
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> actualizarEstadoBotonAsistencia(asistenciasHoy));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: seguimos usando la última configuración cacheada en SessionManager.
            }
        });
    }

    private void verificarPermisosGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacionYGuardar();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacionYGuardar() {
        btnMarcarAsistencia.setEnabled(false);
        btnMarcarAsistencia.setText("Obteniendo GPS...");

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        guardarAsistenciaEnRoom(location.getLatitude(), location.getLongitude());
                    } else {
                        restaurarBotonAsistenciaTrasError();
                        Toast.makeText(getContext(), "No se pudo obtener la ubicación. Intenta salir al aire libre.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    restaurarBotonAsistenciaTrasError();
                    Toast.makeText(getContext(), "Error del sensor GPS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void restaurarBotonAsistenciaTrasError() {
        if (usuarioActual == null) {
            actualizarEstadoBotonAsistencia(0);
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            int asistenciasHoy = contarAsistenciasHoy(usuarioActual.id);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> actualizarEstadoBotonAsistencia(asistenciasHoy));
            }
        });
    }

    private boolean isHoraAutomaticaActivada() {
        try {
            int isAutoTime = android.provider.Settings.Global.getInt(requireContext().getContentResolver(),
                    android.provider.Settings.Global.AUTO_TIME
            );
            return isAutoTime == 1;
        } catch (android.provider.Settings.SettingNotFoundException e) {
            return false;
        }
    }

    private void guardarAsistenciaEnRoom(double latitud, double longitud) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            long timestampActual = System.currentTimeMillis();
            java.text.SimpleDateFormat sdfOdoo = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());

            Asistencia nuevaAsistencia = new Asistencia();
            nuevaAsistencia.id = UUID.randomUUID().toString();
            nuevaAsistencia.usuario_id = usuarioActual.id;
            nuevaAsistencia.fecha_hora = timestampActual;
            nuevaAsistencia.fecha_hora_str = sdfOdoo.format(new java.util.Date(timestampActual));
            nuevaAsistencia.lat_registro = latitud;
            nuevaAsistencia.lon_registro = longitud;
            nuevaAsistencia.sincronizado = false;

            db.asistenciaDao().insertAsistencia(nuevaAsistencia);
            int asistenciasHoy = contarAsistenciasHoy(usuarioActual.id);

            SincronizacionRepository syncRepo = new SincronizacionRepository(requireContext());
            syncRepo.ejecutarSincronizacionCompleta();

            requireActivity().runOnUiThread(() -> {
                actualizarEstadoBotonAsistencia(asistenciasHoy);

                if (getActivity() instanceof MainActivity) {
                    MainActivity mainActivity = (MainActivity) getActivity();
                    mainActivity.asistenciaRegistradaHoy = true;
                    // Marcar asistencia = inicio de jornada: si la empresa activó "Ubicación
                    // en Vivo", arranca el rastreo recién ahora (no al abrir la app).
                    mainActivity.iniciarJornadaUbicacion();
                }
                actualizarVisibilidadBotonFinalizarJornada();

                Toast.makeText(getContext(), "Asistencia guardada correctamente offline.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void cargarResumenRuta() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<RutaParada> rutaHoy = db.rutaParadaDao().getParadasDeHoy();

            int total, visitados, pendientes;
            String proximoNombre = null, proximoDireccion = null;

            if (!rutaHoy.isEmpty()) {
                // La empresa usa planificación de rutas y hay paradas para hoy: el resumen
                // refleja solo esos clientes, no la lista completa de asignados.
                List<Integer> idsVisitados = db.clienteDao().getIdsVisitados();
                total = rutaHoy.size();
                int contadorVisitados = 0;
                RutaParada proximaParada = null;
                for (RutaParada parada : rutaHoy) {
                    if (idsVisitados.contains(parada.customerId)) {
                        contadorVisitados++;
                    } else if (proximaParada == null) {
                        proximaParada = parada;
                    }
                }
                visitados = contadorVisitados;
                pendientes = total - visitados;
                if (proximaParada != null) {
                    proximoNombre = proximaParada.customerName;
                    proximoDireccion = "Parada " + proximaParada.secuencia + " de tu ruta de hoy";
                }
            } else {
                // Sin ruta planificada (feature apagada o sin paradas para hoy): comportamiento
                // original, se muestran todos los clientes asignados al vendedor.
                total = db.clienteDao().getTotalClientesRuta();
                visitados = db.clienteDao().getClientesVisitadosRuta();
                pendientes = total - visitados;
                Cliente proximo = db.clienteDao().getProximoClientePendiente();
                if (proximo != null) {
                    proximoNombre = proximo.nombre_fundo;
                    proximoDireccion = "Contacto: " + proximo.nombre_contacto;
                }
            }

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            int visitasHoy = db.visitaDao().getVisitasHoy(cal.getTimeInMillis());

            int totalFinal = total, visitadosFinal = visitados, pendientesFinal = pendientes;
            String nombreFinal = proximoNombre, direccionFinal = proximoDireccion;

            requireActivity().runOnUiThread(() -> {
                tvProgresoClientes.setText(visitadosFinal + "/" + totalFinal);
                tvPendientesCount.setText(String.valueOf(pendientesFinal));
                tvReprogramadosCount.setText(String.valueOf(visitasHoy));

                if (totalFinal > 0) {
                    int porcentaje = (visitadosFinal * 100) / totalFinal;
                    progressBarClientes.setProgress(porcentaje);
                } else {
                    progressBarClientes.setProgress(0);
                }

                if (nombreFinal != null) {
                    tvProximoClienteNombre.setText(nombreFinal);
                    tvProximoClienteDireccion.setText(direccionFinal);
                } else if (totalFinal > 0 && pendientesFinal == 0) {
                    tvProximoClienteNombre.setText("¡Ruta Completada!");
                    tvProximoClienteDireccion.setText("Buen trabajo hoy.");
                } else {
                    tvProximoClienteNombre.setText("Sin ruta asignada");
                    tvProximoClienteDireccion.setText("Habla con el administrador.");
                }
            });
        });
    }
}