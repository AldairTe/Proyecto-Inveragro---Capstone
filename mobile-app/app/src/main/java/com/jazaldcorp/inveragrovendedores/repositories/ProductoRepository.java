package com.jazaldcorp.inveragrovendedores.repositories;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.dao.ProductoDao;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Producto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductoRepository {

    private final ProductoDao productoDao;
    private final ApiService apiService;
    private final ExecutorService executor;
    private final Context context;

    public ProductoRepository(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        productoDao = db.productoDao();
        apiService = ApiClient.getService(context);
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Producto>> getProductosLocales() {
        return productoDao.getProductosLive();
    }

    @SuppressWarnings("unchecked")
    public void actualizarCatalogoDesdeServidor() {
        String token = obtenerTokenGuardado();
        if (token == null || token.isEmpty()) return;

        apiService.getCatalogo("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;

                Object productosObj = result.get("products");
                if (!(productosObj instanceof List)) return;

                List<Map<String, Object>> rawList = (List<Map<String, Object>>) productosObj;
                List<Producto> productos = new ArrayList<>();

                for (Map<String, Object> map : rawList) {
                    Producto p = new Producto();

                    Object idObj = map.get("id");
                    if (idObj instanceof Number) p.id = ((Number) idObj).intValue();

                    p.nombre = map.get("name") != null ? map.get("name").toString() : "";
                    p.codigo = map.get("default_code") != null ? map.get("default_code").toString() : "";
                    p.unidadMedida = map.get("uom_name") != null ? map.get("uom_name").toString() : "";

                    Object priceObj = map.get("price");
                    if (priceObj instanceof Number) p.precio = ((Number) priceObj).doubleValue();

                    Object stockObj = map.get("qty_available");
                    p.stockDisponible = stockObj instanceof Number ? ((Number) stockObj).doubleValue() : null;

                    if (p.id != null) productos.add(p);
                }

                executor.execute(() -> {
                    productoDao.borrarTodos();
                    productoDao.insertProductos(productos);
                });
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: seguimos mostrando el catálogo cacheado localmente.
            }
        });
    }

    private String obtenerTokenGuardado() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE);
        return prefs.getString("auth_token", null);
    }
}
