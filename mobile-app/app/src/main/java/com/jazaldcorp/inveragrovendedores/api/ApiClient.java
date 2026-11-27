package com.jazaldcorp.inveragrovendedores.api;

import android.content.Context;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit = null;
    private static String retrofitBaseUrl = null;

    /**
     * La URL del servidor ya no es una constante: cada empresa cliente instala su propio
     * Odoo con su propia URL, así que se configura una sola vez en ActivationActivity y
     * queda guardada en SessionManager. Este método reconstruye el cliente Retrofit si la
     * URL configurada cambió (p. ej. al reactivar la app apuntando a otro servidor).
     */
    public static ApiService getService(Context context) {
        SessionManager sessionManager = new SessionManager(context.getApplicationContext());
        String baseUrl = sessionManager.getServerUrl();

        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("La app aún no fue activada: falta configurar la URL del servidor Odoo.");
        }

        if (retrofit == null || !baseUrl.equals(retrofitBaseUrl)) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        String token = sessionManager.getAuthToken();

                        Request.Builder requestBuilder = original.newBuilder();
                        if (token != null && !token.isEmpty()) {
                            requestBuilder.header("Authorization", "Bearer " + token);
                        }

                        return chain.proceed(requestBuilder.build());
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            retrofitBaseUrl = baseUrl;
        }
        return retrofit.create(ApiService.class);
    }

    /**
     * Cliente "pelado" para /field_sales/api/activate: se usa antes de guardar la URL en
     * SessionManager (aún no hay sesión ni URL persistida), apuntando a la URL que el
     * usuario acaba de escribir en la pantalla de activación.
     */
    public static ApiService getActivationService(String serverUrlSinGuardar) {
        String baseUrl = serverUrlSinGuardar.trim();
        if (!baseUrl.endsWith("/")) baseUrl = baseUrl + "/";

        Retrofit tempRetrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return tempRetrofit.create(ApiService.class);
    }
}
