package com.jazaldcorp.inveragrovendedores.activities;

import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Usuario;
import com.jazaldcorp.inveragrovendedores.repositories.BrandingRepository;
import com.jazaldcorp.inveragrovendedores.utils.BrandingHelper;
import com.jazaldcorp.inveragrovendedores.utils.PasswordHashUtil;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import android.widget.ImageView;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etDni, etPassword;
    private Button btnLogin;
    private ImageView imgLogo;
    private ApiService apiService;
    private AppDatabase db;
    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_login);

        // 1. Enlazar vistas
        etDni = findViewById(R.id.etDni);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        imgLogo = findViewById(R.id.imgLogo);

        session = new SessionManager(this);
        aplicarBrandingCacheado();

        // Si el dispositivo aún no fue activado (no hay URL de servidor configurada),
        // no tiene sentido mostrar el login: primero hay que configurar a qué Odoo apunta.
        if (!session.isActivated()) {
            startActivity(new Intent(this, ActivationActivity.class));
            finish();
            return;
        }

        // Si ya tiene sesión, salta directo al Dashboard
        if (session.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish(); // Cierra el login
            return; // Detiene la ejecución del resto del onCreate
        }

        // 2. Inicializar Retrofit y Room
        apiService = ApiClient.getService(this);
        db = AppDatabase.getInstance(this);

        // Refresca la marca por si cambió desde la última vez (logo/color configurados en Odoo)
        new BrandingRepository(this).actualizarBranding((appName, colorHex, logoBase64) ->
                runOnUiThread(this::aplicarBrandingCacheado));

        // 3. Evento de click en el botón
        btnLogin.setOnClickListener(v -> {
            String dni = etDni.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (dni.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false); // Bloquear botón para evitar doble click
            iniciarSesion(dni, password);
        });
    }

    private void aplicarBrandingCacheado() {
        BrandingHelper.aplicarLogo(imgLogo, session.getBrandingLogoBase64());
        BrandingHelper.aplicarColorFondo(btnLogin, session.getBrandingColor());
    }

    private void iniciarSesion(String dni, String password) {
        // 1. Credenciales internas
        Map<String, String> credenciales = new HashMap<>();
        credenciales.put("login", dni);
        credenciales.put("password", password);

        // 2. Estructura que exige JSON-RPC
        Map<String, Object> bodyJsonRpc = new HashMap<>();
        bodyJsonRpc.put("jsonrpc", "2.0");
        bodyJsonRpc.put("method", "call");
        bodyJsonRpc.put("params", credenciales);
        bodyJsonRpc.put("id", null);

        // 3. Llamada con Retrofit
        Call<Map<String, Object>> call = apiService.login(bodyJsonRpc);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                btnLogin.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();

                    // Nota: Al usar type='json', Odoo devuelve la respuesta dentro de una llave "result"
                    Map<String, Object> result = (Map<String, Object>) body.get("result");

                    if (result != null && result.containsKey("error")) {
                        Toast.makeText(LoginActivity.this, "" + result.get("error"), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (result != null) {
                        String token = (String) result.get("token");
                        Map<String, Object> employeeMap = (Map<String, Object>) result.get("employee");

                        if (employeeMap != null && token != null) {
                            Usuario usuarioLogueado = new Usuario();
                            Object idObj = employeeMap.get("id");
                            if (idObj instanceof Number) {
                                usuarioLogueado.id = ((Number) idObj).intValue();
                            }
                            usuarioLogueado.dni = (String) employeeMap.get("seller_code");
                            usuarioLogueado.nombre_completo = (String) employeeMap.get("name");
                            usuarioLogueado.estado = true;
                            usuarioLogueado.token = token;
                            usuarioLogueado.passwordHashLocal = PasswordHashUtil.hash(dni, password);

                            Object expiresAtObj = result.get("expires_at");
                            if (expiresAtObj != null) {
                                session.saveTokenExpiresAt(String.valueOf(expiresAtObj));
                            }

                            guardarUsuarioLocalmente(usuarioLogueado, token);
                        } else {
                            Toast.makeText(LoginActivity.this, "Respuesta incompleta del servidor", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Credenciales incorrectas o usuario inactivo", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Respuesta HTTP no exitosa (ej. servidor caído): intentamos login offline
                    // antes de rendirnos, por si ya se inició sesión antes en este dispositivo.
                    intentarLoginOffline(dni, password, "Error de conexión con el servidor.");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin red/DNS/timeout: intentamos login offline con las credenciales cacheadas
                // del último login exitoso, para que el vendedor pueda seguir trabajando.
                intentarLoginOffline(dni, password, "Error al conectar con Odoo: " + t.getMessage());
            }
        });
    }

    private void intentarLoginOffline(String dni, String password, String mensajeErrorSiFalla) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Usuario local = AppDatabase.getInstance(this).usuarioDao().loginLocal(dni);
            String hashIngresado = PasswordHashUtil.hash(dni, password);

            boolean coincide = local != null && local.passwordHashLocal != null
                    && local.passwordHashLocal.equals(hashIngresado) && local.token != null;

            runOnUiThread(() -> {
                btnLogin.setEnabled(true);
                if (coincide) {
                    session.createSession(local.token);
                    Toast.makeText(this, "Sin conexión: ingresaste en modo offline con tu último acceso guardado.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, mensajeErrorSiFalla, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    private void guardarUsuarioLocalmente(Usuario usuario, String token) {
        // Room no permite escribir en la BD en el hilo principal (UI thread),
        // así que usamos un Executor de un solo hilo.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // Limpiamos usuarios anteriores por seguridad y guardamos el nuevo
            db.usuarioDao().deleteAll();
            db.usuarioDao().insertUsuario(usuario);

            // Guardamos la sesión y el token Bearer de forma segura en SharedPreferences
            session.createSession(token);

            // Volvemos al hilo principal para cambiar de pantalla
            runOnUiThread(() -> {
                Toast.makeText(LoginActivity.this, "¡Bienvenido, " + usuario.nombre_completo + "!", Toast.LENGTH_SHORT).show();

                // Navegar al Dashboard
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Cerramos el Login para que no pueda volver atrás con el botón de retroceso
            });
        });
    }
}