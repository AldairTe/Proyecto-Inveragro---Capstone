package com.jazaldcorp.inveragrovendedores.repositories;

import android.content.Context;

import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.dao.RutaParadaDao;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.RutaParada;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Consulta la ruta planificada para hoy (feature configurable por empresa en Odoo) y la
 * cachea localmente. Si la empresa no usa esta función, o no hay paradas para hoy, la tabla
 * local simplemente queda vacía y las pantallas caen de vuelta a mostrar todos los clientes.
 */
public class RouteRepository {

    private final RutaParadaDao rutaParadaDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Context context;

    public RouteRepository(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        rutaParadaDao = db.rutaParadaDao();
        apiService = ApiClient.getService(context);
        executor = Executors.newSingleThreadExecutor();
    }

    @SuppressWarnings("unchecked")
    public void actualizarRutaDeHoy(Runnable onFinished) {
        String token = new SessionManager(context).getAuthToken();
        if (token == null || token.isEmpty()) return;

        apiService.getRutaHoy("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;
                if (result.get("error") != null) return;

                boolean enabled = Boolean.TRUE.equals(result.get("enabled"));
                Object stopsObj = result.get("stops");
                List<Map<String, Object>> stops = (stopsObj instanceof List) ? (List<Map<String, Object>>) stopsObj : null;

                List<RutaParada> paradas = new ArrayList<>();
                if (enabled && stops != null) {
                    for (Map<String, Object> stop : stops) {
                        RutaParada p = new RutaParada();
                        Object idObj = stop.get("customer_id");
                        p.customerId = idObj instanceof Number ? ((Number) idObj).intValue() : 0;
                        p.customerName = stop.get("customer_name") != null ? stop.get("customer_name").toString() : "";
                        Object seqObj = stop.get("sequence");
                        p.secuencia = seqObj instanceof Number ? ((Number) seqObj).intValue() : 0;
                        p.visitado = Boolean.TRUE.equals(stop.get("done"));
                        paradas.add(p);
                    }
                }

                executor.execute(() -> {
                    rutaParadaDao.borrarTodas();
                    if (!paradas.isEmpty()) rutaParadaDao.insertParadas(paradas);
                    if (onFinished != null) onFinished.run();
                });
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: seguimos con la ruta cacheada del último fetch exitoso.
            }
        });
    }
}
