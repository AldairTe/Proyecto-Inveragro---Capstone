package com.jazaldcorp.inveragrovendedores.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla que se muestra una sola vez, antes del Login: configura a qué Odoo (el de la
 * empresa cliente) apunta esta instalación de la app, validando un código de activación
 * generado desde Odoo > Fuerza de Ventas > Configuración > Activación App Móvil. Esto evita
 * que cualquiera que descargue el APK pueda apuntar la app a un servidor arbitrario sin
 * conocer ese código.
 */
public class ActivationActivity extends AppCompatActivity {

    private TextInputEditText etServerUrl, etActivationCode;
    private MaterialButton btnActivar;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        session = new SessionManager(this);

        etServerUrl = findViewById(R.id.etServerUrl);
        etActivationCode = findViewById(R.id.etActivationCode);
        btnActivar = findViewById(R.id.btnActivar);

        btnActivar.setOnClickListener(v -> intentarActivar());
    }

    private void intentarActivar() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String code = etActivationCode.getText().toString().trim();

        if (serverUrl.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Completa la URL del servidor y el código de activación.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://" + serverUrl;
        }

        btnActivar.setEnabled(false);
        btnActivar.setText("Validando...");

        Map<String, Object> params = new HashMap<>();
        params.put("activation_code", code);
        params.put("device_id", obtenerDeviceId());
        params.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);

        Map<String, Object> bodyJsonRpc = new HashMap<>();
        bodyJsonRpc.put("jsonrpc", "2.0");
        bodyJsonRpc.put("method", "call");
        bodyJsonRpc.put("params", params);
        bodyJsonRpc.put("id", null);

        ApiService activationService = ApiClient.getActivationService(serverUrl);
        String finalServerUrl = serverUrl;

        activationService.activate(bodyJsonRpc).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                restaurarBoton();

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(ActivationActivity.this, "No se pudo contactar al servidor. Revisa la URL.", Toast.LENGTH_LONG).show();
                    return;
                }

                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) {
                    Toast.makeText(ActivationActivity.this, "Respuesta inesperada del servidor.", Toast.LENGTH_LONG).show();
                    return;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resultObj;
                boolean valido = Boolean.TRUE.equals(result.get("valid"));

                if (!valido) {
                    String error = String.valueOf(result.get("error"));
                    Toast.makeText(ActivationActivity.this, error, Toast.LENGTH_LONG).show();
                    return;
                }

                session.saveServerConfig(finalServerUrl);
                // Cacheamos la marca (logo/color) apenas se confirma el servidor, para que
                // ya esté lista cuando se abra el Login.
                new com.jazaldcorp.inveragrovendedores.repositories.BrandingRepository(ActivationActivity.this)
                        .actualizarBranding(null);
                Toast.makeText(ActivationActivity.this, "Dispositivo activado correctamente.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(ActivationActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                restaurarBoton();
                Toast.makeText(ActivationActivity.this, "Error de conexión: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void restaurarBoton() {
        btnActivar.setEnabled(true);
        btnActivar.setText("ACTIVAR");
    }

    @SuppressLint("HardwareIds")
    private String obtenerDeviceId() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return androidId != null ? androidId : "unknown-device";
    }
}
