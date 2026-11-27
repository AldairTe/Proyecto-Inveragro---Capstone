package com.jazaldcorp.inveragrovendedores.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.jazaldcorp.inveragrovendedores.activities.MainActivity;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import com.jazaldcorp.inveragrovendedores.models.RutaParada;
import com.jazaldcorp.inveragrovendedores.repositories.LocationRepository;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio en primer plano que envía la ubicación del vendedor periódicamente (incluso con la
 * app minimizada), mientras la empresa tenga habilitada "Ubicación en Vivo" en Odoo. Arranca
 * explícitamente al marcar asistencia (o al iniciar sesión si la empresa no usa asistencia) y
 * se detiene con "Finalizar Jornada" o al cerrar sesión.
 *
 * También revisa, en cada punto capturado, si el vendedor entró al radio de un cliente de su
 * ruta de hoy aún no visitado, y le sugiere registrar la visita con una notificación local.
 *
 * Requiere permiso ACCESS_BACKGROUND_LOCATION, otorgado explícitamente por el usuario. Muestra
 * una notificación persistente mientras está activo, como exige Android para cualquier
 * rastreo en segundo plano.
 */
public class LocationTrackingService extends Service {

    private static final String CHANNEL_ID = "ubicacion_en_vivo_channel";
    private static final String CHANNEL_ID_GEOCERCA = "geocerca_channel";
    private static final int NOTIFICATION_ID = 1001;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRepository locationRepository;
    private AppDatabase db;
    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRepository = new LocationRepository(this);
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        crearCanalesNotificacion();
        startForeground(NOTIFICATION_ID, construirNotificacion());

        int intervaloSegundos = new SessionManager(this).getLocationTrackingIntervalSeconds();
        long intervaloMillis = Math.max(intervaloSegundos, 20) * 1000L; // mínimo 20s para cuidar batería/datos

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervaloMillis)
                .setMinUpdateIntervalMillis(intervaloMillis / 2)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    locationRepository.registrarUbicacion(location);
                    verificarGeocercas(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());

        return START_STICKY; // Si Android mata el servicio por recursos, intenta reiniciarlo
    }

    /** Compara la posición actual contra los clientes de la ruta de hoy aún no visitados. */
    private void verificarGeocercas(Location location) {
        executor.execute(() -> {
            SessionManager session = new SessionManager(this);
            int radio = session.getGeofenceRadiusMeters();
            String hoyStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());

            List<RutaParada> paradas = db.rutaParadaDao().getParadasDeHoy();
            for (RutaParada parada : paradas) {
                if (parada.visitado) continue;
                if (session.yaNotificoGeocercaHoy(parada.customerId, hoyStr)) continue;

                Cliente cliente = db.clienteDao().getClienteById(parada.customerId);
                if (cliente == null || (cliente.latitud == 0.0 && cliente.longitud == 0.0)) continue;

                Location destino = new Location("cliente");
                destino.setLatitude(cliente.latitud);
                destino.setLongitude(cliente.longitud);

                if (location.distanceTo(destino) <= radio) {
                    session.marcarGeocercaNotificada(parada.customerId, hoyStr);
                    mostrarNotificacionGeocerca(parada.customerId, cliente.nombre_fundo);
                }
            }
        });
    }

    private void mostrarNotificacionGeocerca(int clienteId, String nombreCliente) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_ABRIR_CLIENTE_ID, clienteId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, clienteId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_GEOCERCA)
                .setContentTitle("Estás cerca de " + nombreCliente)
                .setContentText("Toca para ver el cliente y registrar la visita.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(2000 + clienteId, notification);
    }

    private void crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel canalServicio = new NotificationChannel(
                    CHANNEL_ID, "Ubicación en Vivo", NotificationManager.IMPORTANCE_LOW);
            canalServicio.setDescription("Indica que la app está compartiendo tu ubicación con la empresa durante la jornada.");
            manager.createNotificationChannel(canalServicio);

            NotificationChannel canalGeocerca = new NotificationChannel(
                    CHANNEL_ID_GEOCERCA, "Cerca de un cliente", NotificationManager.IMPORTANCE_HIGH);
            canalGeocerca.setDescription("Avisa cuando llegas cerca de un cliente de tu ruta del día.");
            manager.createNotificationChannel(canalGeocerca);
        }
    }

    private Notification construirNotificacion() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ubicación en vivo activa")
                .setContentText("Compartiendo tu ubicación con la empresa durante la jornada.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
