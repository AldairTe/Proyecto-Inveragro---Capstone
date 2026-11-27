package com.jazaldcorp.inveragrovendedores.repositories;

import android.content.Context;

import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Consulta el nombre/color/logo configurados en Odoo > Configuración > Marca (White-Label)
 * y los cachea localmente. Es un endpoint público (no requiere login), así que se puede
 * llamar apenas se activa el dispositivo o al abrir el login.
 */
public class BrandingRepository {

    public interface OnBrandingLoaded {
        void onLoaded(String appName, String colorHex, String logoBase64);
    }

    private final ApiService apiService;
    private final SessionManager session;

    public BrandingRepository(Context context) {
        this.apiService = ApiClient.getService(context);
        this.session = new SessionManager(context);
    }

    @SuppressWarnings("unchecked")
    public void actualizarBranding(OnBrandingLoaded callback) {
        apiService.getBranding(new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;

                String appName = result.get("app_display_name") != null ? result.get("app_display_name").toString() : null;
                String color = result.get("primary_color") != null ? result.get("primary_color").toString() : null;
                String logoB64 = result.get("logo_b64") != null ? result.get("logo_b64").toString() : null;

                session.saveBranding(appName, color, logoB64);
                if (callback != null) callback.onLoaded(appName, color, logoB64);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: se sigue usando la marca cacheada (o la genérica por defecto).
            }
        });
    }
}
