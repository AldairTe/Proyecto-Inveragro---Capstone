package com.jazaldcorp.inveragrovendedores.repositories;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.dao.ClienteDao;
import com.jazaldcorp.inveragrovendedores.models.Cliente;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ClienteRepository {

    private ClienteDao clienteDao;
    private ApiService apiService;
    private ExecutorService executor;
    AppDatabase db;
    private Context context;

    public ClienteRepository(Context context) {
        this.context = context;
        db = AppDatabase.getInstance(context);
        clienteDao = db.clienteDao();
        apiService = ApiClient.getService(context);
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Cliente>> getClientesLocales() {
        return clienteDao.getAllClientesLive();
    }
    public void actualizarClientesDesdeServidor() {
        executor.execute(() -> {
            String token = obtenerTokenGuardado();
            if (token == null || token.isEmpty()) {
                return;
            }

            apiService.getClientes("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> body = response.body();

                        Map<String, Object> result = (Map<String, Object>) body.get("result");
                        if (result == null) {
                            result = body;
                        }

                        List<Map<String, Object>> rawClientesList = null;
                        try {
                            rawClientesList = (List<Map<String, Object>>) result.get("customers");
                        } catch (Exception ignored) {
                        }

                        if (rawClientesList != null && !rawClientesList.isEmpty()) {
                            List<Cliente> clientesList = new ArrayList<>();

                            for (Map<String, Object> map : rawClientesList) {
                                Cliente cliente = new Cliente();

                                Object idObj = map.get("id");
                                if (idObj instanceof Number) {
                                    cliente.id = ((Number) idObj).intValue();
                                }

                                cliente.nombre_contacto = map.get("contact_name") != null ? map.get("contact_name").toString() : null;
                                cliente.nombre_fundo = map.get("name") != null ? map.get("name").toString() : null;
                                cliente.telefono = map.get("phone") != null ? map.get("phone").toString() : null;

                                Object latObj = map.get("latitude");
                                if (latObj instanceof Number) {
                                    cliente.latitud = ((Number) latObj).doubleValue();
                                }

                                Object lonObj = map.get("longitude");
                                if (lonObj instanceof Number) {
                                    cliente.longitud = ((Number) lonObj).doubleValue();
                                }

                                Object uuidObj = map.get("external_uuid");
                                if (uuidObj instanceof String) {
                                    cliente.external_uuid = (String) uuidObj;
                                } else if (uuidObj != null) {
                                    cliente.external_uuid = uuidObj.toString();
                                } else {
                                    cliente.external_uuid = null;
                                }

                                clientesList.add(cliente);
                            }

                            executor.execute(() -> {
                                clienteDao.insertClientes(clientesList);
                            });
                        }
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                }
            });
        });
    }

    private String obtenerTokenGuardado() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE);
        return prefs.getString("auth_token", null);
    }

}