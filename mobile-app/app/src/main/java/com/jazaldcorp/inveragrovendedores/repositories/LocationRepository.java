package com.jazaldcorp.inveragrovendedores.repositories;

import android.content.Context;
import android.location.Location;
import android.os.BatteryManager;
import android.util.Log;

import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.UbicacionPing;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Envía cada punto de ubicación capturado por LocationTrackingService, junto con el nivel de
 * batería del celular. Si no hay señal (muy común en campo agrícola), lo guarda en Room y
 * SincronizacionRepository lo reintenta después, igual que visitas/pedidos/asistencias.
 */
public class LocationRepository {

    private static final String TAG = "LocationRepo";

    private final AppDatabase db;
    private final Context context;
    private final ExecutorService executor;

    public LocationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void registrarUbicacion(Location location) {
        executor.execute(() -> {
            String token = new SessionManager(context).getAuthToken();
            if (token == null || token.isEmpty()) return;

            SimpleDateFormat sdfOdoo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String capturedAtStr = sdfOdoo.format(new Date());
            int bateria = obtenerNivelBateria();

            boolean enviado = intentarEnviar(token, location.getLatitude(), location.getLongitude(),
                    location.getAccuracy(), bateria, capturedAtStr);

            if (!enviado) {
                // Sin señal: se encola para que SincronizacionRepository lo reintente.
                UbicacionPing ping = new UbicacionPing();
                ping.latitude = location.getLatitude();
                ping.longitude = location.getLongitude();
                ping.accuracy = location.getAccuracy();
                ping.batteryLevel = bateria;
                ping.capturedAtStr = capturedAtStr;
                ping.sincronizado = false;
                db.ubicacionPingDao().insertPing(ping);
            }
        });
    }

    private int obtenerNivelBateria() {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return -1;
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean intentarEnviar(String token, double lat, double lng, float accuracy, int bateria, String capturedAtStr) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("latitude", lat);
            params.put("longitude", lng);
            params.put("accuracy", accuracy);
            if (bateria >= 0) params.put("battery_level", bateria);
            params.put("captured_at", capturedAtStr);

            Map<String, Object> payload = new HashMap<>();
            payload.put("params", params);

            ApiService apiService = ApiClient.getService(context);
            Call<Map<String, Object>> call = apiService.createLocationPing("Bearer " + token, payload);
            Response<Map<String, Object>> response = call.execute();
            return response.isSuccessful() && response.body() != null;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo enviar el ping de ubicación, se encola: " + e.getMessage());
            return false;
        }
    }

    /** Llamado por SincronizacionRepository para reintentar los pings encolados offline. */
    public void sincronizarPingsPendientes(String authHeader) {
        List<UbicacionPing> pendientes = db.ubicacionPingDao().getPingsPendientes();
        if (pendientes.isEmpty()) return;

        ApiService apiService = ApiClient.getService(context);
        for (UbicacionPing ping : pendientes) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("latitude", ping.latitude);
                params.put("longitude", ping.longitude);
                params.put("accuracy", ping.accuracy);
                if (ping.batteryLevel >= 0) params.put("battery_level", ping.batteryLevel);
                params.put("captured_at", ping.capturedAtStr);

                Map<String, Object> payload = new HashMap<>();
                payload.put("params", params);

                Response<Map<String, Object>> response = apiService.createLocationPing(authHeader, payload).execute();
                if (response.isSuccessful()) {
                    db.ubicacionPingDao().marcarComoSincronizado(ping.id);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reintentando ping encolado: " + e.getMessage());
            }
        }
        db.ubicacionPingDao().borrarSincronizados();
    }
}
