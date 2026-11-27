package com.jazaldcorp.inveragrovendedores.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.fragments.DashboardFragment;
import com.jazaldcorp.inveragrovendedores.fragments.DetalleClienteFragment;
import com.jazaldcorp.inveragrovendedores.fragments.ListaClientesFragment;
import com.jazaldcorp.inveragrovendedores.fragments.MapaFragment;
import com.jazaldcorp.inveragrovendedores.fragments.ProductosFragment;
import com.jazaldcorp.inveragrovendedores.fragments.ProfileFragment;
import com.jazaldcorp.inveragrovendedores.services.LocationTrackingService;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_ABRIR_CLIENTE_ID = "ABRIR_CLIENTE_ID";

    private BottomNavigationView bottomNavigationView;

    private AlertDialog dialogUbicacion;
    private BroadcastReceiver gpsReceiver;

    private AppDatabase db;
    private SessionManager sessionManager; // Añadido para gestionar el token
    public boolean asistenciaRegistradaHoy = false;

    private final ActivityResultLauncher<String> fineLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    solicitarPermisoUbicacionSegundoPlano();
                }
            });

    private final ActivityResultLauncher<String> backgroundLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    iniciarServicioUbicacion();
                } else {
                    Toast.makeText(this, "Sin el permiso de ubicación en segundo plano, la empresa no podrá ver tu posición cuando la app esté minimizada.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        // 🛡️ Doble seguridad: Si por alguna razón no hay sesión o token, lo devolvemos al Login
        if (!sessionManager.isLoggedIn() || sessionManager.getAuthToken() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Apenas arranca la app, verificamos silenciosamente
        cargarEstadoAsistenciaSilencioso();
        verificarConfigUbicacionEnVivo();

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarBottomNav(
                bottomNavigationView, sessionManager.getBrandingColor());

        // Cargar el Dashboard por defecto al abrir la app
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
        }

        // Escuchar los clicks en el menú inferior
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // --- 🛡️ BARRERA DE SEGURIDAD (UX OPTIMIZADO) ---
            if (itemId != R.id.nav_dashboard && !asistenciaRegistradaHoy) {
                mostrarAlertaAsistencia();
                return false;
            }
            // -----------------------------------------------

            Fragment fragmentSeleccionado = null;

            if (itemId == R.id.nav_dashboard) {
                fragmentSeleccionado = new DashboardFragment();
            } else if (itemId == R.id.nav_mapa) {
                fragmentSeleccionado = new MapaFragment();
            } else if (itemId == R.id.nav_cliente) {
                fragmentSeleccionado = new ListaClientesFragment();
            } else if (itemId == R.id.nav_productos) {
                fragmentSeleccionado = new ProductosFragment();
            } else if (itemId == R.id.nav_perfil) {
                fragmentSeleccionado = new ProfileFragment();
            }

            if (fragmentSeleccionado != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragmentSeleccionado)
                        .commit();
                return true;
            }
            return false;
        });

        manejarIntentDeGeocerca(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        manejarIntentDeGeocerca(intent);
    }

    /** Si la app se abrió desde la notificación de geocerca, salta directo al detalle del cliente. */
    private void manejarIntentDeGeocerca(Intent intent) {
        if (intent == null) return;
        int clienteId = intent.getIntExtra(EXTRA_ABRIR_CLIENTE_ID, -1);
        if (clienteId == -1) return;
        intent.removeExtra(EXTRA_ABRIR_CLIENTE_ID); // evita reabrir el mismo detalle en rotaciones, etc.

        DetalleClienteFragment detalleFragment = new DetalleClienteFragment();
        Bundle args = new Bundle();
        args.putInt("CLIENTE_ID", clienteId);
        detalleFragment.setArguments(args);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, detalleFragment)
                .addToBackStack(null)
                .commit();
    }

    private void verificarEstadoGPS() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = false;

        try {
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!isGpsEnabled) {
            mostrarAlertaGPS();
        } else {
            if (dialogUbicacion != null && dialogUbicacion.isShowing()) {
                dialogUbicacion.dismiss();
            }
        }
    }

    private void mostrarAlertaGPS() {
        if (dialogUbicacion == null) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle("Ubicación Desactivada");
            builder.setMessage("Tu GPS está apagado. Es obligatorio mantener la ubicación activada para usar la aplicación y registrar las visitas.");
            builder.setCancelable(false);
            builder.setPositiveButton("ACTIVAR GPS", (dialog, which) -> {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            });
            dialogUbicacion = builder.create();
        }

        if (!dialogUbicacion.isShowing()) {
            dialogUbicacion.show();
        }
    }

    private void cargarEstadoAsistenciaSilencioso() {
        // Si la empresa desactivó la asistencia (módulo configurable en Odoo), no hay
        // nada que marcar: no debe bloquear el acceso al mapa/clientes.
        if (!sessionManager.isAttendanceEnabled()) {
            asistenciaRegistradaHoy = true;
            return;
        }

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            var usuarioActual = db.usuarioDao().getUsuarioLogueado();
            if (usuarioActual != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long inicioDeHoy = cal.getTimeInMillis();

                // Nota: usuarioActual.id ahora es int, por lo que encaja perfectamente con Room
                int asistencias = db.asistenciaDao().verificarAsistenciaHoy(usuarioActual.id, inicioDeHoy);

                asistenciaRegistradaHoy = (asistencias > 0);
            }
        });
    }

    /**
     * Consulta si la empresa activó "Ubicación en Vivo" en Odoo. Si sí, arranca el flujo de
     * permisos (ubicación normal, luego segundo plano) y el servicio de rastreo. Si ya se
     * concedieron los permisos antes, arranca directo sin volver a preguntar.
     */
    private void verificarConfigUbicacionEnVivo() {
        String token = sessionManager.getAuthToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = ApiClient.getService(this);
        apiService.getLocationConfig("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;
                if (result.get("error") != null) return;

                boolean enabled = Boolean.TRUE.equals(result.get("enabled"));
                Object intervalObj = result.get("interval_seconds");
                int interval = intervalObj instanceof Number ? ((Number) intervalObj).intValue() : 60;
                Object radiusObj = result.get("geofence_radius_meters");
                int radius = radiusObj instanceof Number ? ((Number) radiusObj).intValue() : 150;
                sessionManager.saveLocationTrackingConfig(enabled, interval, radius);

                if (!enabled) {
                    stopService(new Intent(MainActivity.this, LocationTrackingService.class));
                }
                // Si está habilitado, ya NO arrancamos aquí automáticamente: el rastreo se
                // inicia explícitamente cuando el vendedor marca su asistencia ("inicio de
                // jornada") y se detiene con el botón "Finalizar Jornada" del Dashboard.
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: seguimos con la última config cacheada (no forzamos arranque
                // aquí; eso lo dispara explícitamente "Marcar Asistencia" en el Dashboard).
            }
        });
    }

    /**
     * Llamado desde DashboardFragment cuando el vendedor marca su asistencia (inicio de
     * jornada). Si la empresa tiene "Ubicación en Vivo" activa, arranca el flujo de permisos
     * y el servicio de rastreo. Si la función está apagada, no hace nada.
     */
    public void iniciarJornadaUbicacion() {
        if (sessionManager.isLocationTrackingEnabled()) {
            solicitarPermisoUbicacionPrimerPlano();
        }
    }

    /** Llamado desde el botón "Finalizar Jornada" del Dashboard. */
    public void finalizarJornadaUbicacion() {
        stopService(new Intent(this, LocationTrackingService.class));
    }

    private void solicitarPermisoUbicacionPrimerPlano() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            solicitarPermisoUbicacionSegundoPlano();
        } else {
            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void solicitarPermisoUbicacionSegundoPlano() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Antes de Android 10 el permiso normal ya cubre segundo plano.
            iniciarServicioUbicacion();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            iniciarServicioUbicacion();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Ubicación en segundo plano")
                    .setMessage("Tu empresa activó el seguimiento de ubicación en vivo. En la siguiente pantalla, elige \"Permitir todo el tiempo\" para que funcione incluso con la app minimizada.")
                    .setCancelable(false)
                    .setPositiveButton("Continuar", (dialog, which) ->
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    .show();
        }
    }

    private void iniciarServicioUbicacion() {
        Intent intent = new Intent(this, LocationTrackingService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    public void mostrarAlertaAsistencia() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("⛔ Asistencia Requerida")
                .setMessage("Debes registrar tu asistencia antes de ver tus rutas y clientes.")
                .setPositiveButton("ENTENDIDO", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarEstadoGPS();

        if (gpsReceiver == null) {
            gpsReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                        verificarEstadoGPS();
                    }
                }
            };
        }

        registerReceiver(gpsReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gpsReceiver != null) {
            unregisterReceiver(gpsReceiver);
        }
    }
}