package com.jazaldcorp.inveragrovendedores.repositories;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Asistencia;
import com.jazaldcorp.inveragrovendedores.models.Pedido;
import com.jazaldcorp.inveragrovendedores.models.PedidoLinea;
import com.jazaldcorp.inveragrovendedores.models.Visita;
import com.jazaldcorp.inveragrovendedores.models.VisitaMultimedia;
import com.jazaldcorp.inveragrovendedores.models.VisitaResponse;
import com.jazaldcorp.inveragrovendedores.utils.MediaCompressor;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

public class SincronizacionRepository {

    private AppDatabase db;
    private ApiService apiService;
    private ExecutorService executor;
    private Context context;
    private MediaCompressor mediaCompressor;

    public SincronizacionRepository(Context context) {
        this.context = context;
        db = AppDatabase.getInstance(context);
        apiService = ApiClient.getService(context);
        executor = Executors.newSingleThreadExecutor();
        mediaCompressor = new MediaCompressor(context);
    }

    public void ejecutarSincronizacionCompleta() {
        ejecutarSincronizacionCompleta(null);
    }

    /**
     * @param onFinished si no es null, se invoca en el hilo principal al terminar el intento de
     *                    sincronización (haya o no datos pendientes). Usado por la pantalla de
     *                    Ajustes para refrescar "Última sincronización" al presionar el botón.
     */
    public void ejecutarSincronizacionCompleta(Runnable onFinished) {
        executor.execute(() -> {
            String token = obtenerTokenGuardado();
            if (token == null || token.isEmpty()) {
                Log.e("SYNC", "No hay token de sesión disponible para sincronizar.");
                if (onFinished != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(onFinished);
                return;
            }

            String authHeader = "Bearer " + token;

            // Los pings de ubicación se reintentan siempre (no bloquean el resto ni se cuentan
            // como "nada pendiente", ya que se generan constantemente en segundo plano).
            new LocationRepository(context).sincronizarPingsPendientes(authHeader);

            List<Visita> visitasPendientes = db.visitaDao().getVisitasNoSincronizadas();
            List<Asistencia> asistenciasPendientes = db.asistenciaDao().getAsistenciasPendientes();
            List<Pedido> pedidosPendientes = db.pedidoDao().getPedidosNoSincronizados();

            if (visitasPendientes.isEmpty() && asistenciasPendientes.isEmpty() && pedidosPendientes.isEmpty()) {
                Log.d("SYNC", "Todo está al día. No hay datos pendientes por sincronizar.");
                new SessionManager(context).saveUltimaSincronizacionAhora();
                if (onFinished != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(onFinished);
                return;
            }

            Log.d("SYNC", "Iniciando sincronización con Odoo: " + visitasPendientes.size() + " visitas pendientes, "
                    + asistenciasPendientes.size() + " asistencias pendientes, "
                    + pedidosPendientes.size() + " pedidos pendientes.");

            for (Visita visita : visitasPendientes) {
                sincronizarVisitaIndividual(authHeader, visita);
            }
            for (Asistencia asistencia : asistenciasPendientes) {
                sincronizarAsistenciaIndividual(authHeader, asistencia);
            }
            for (Pedido pedido : pedidosPendientes) {
                sincronizarPedidoIndividual(authHeader, pedido);
            }

            new SessionManager(context).saveUltimaSincronizacionAhora();
            if (onFinished != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(onFinished);
        });
    }

    private void sincronizarVisitaIndividual(String authHeader, Visita visita) {
        Map<String, Object> params = new HashMap<>();
        params.put("external_uuid", visita.external_uuid);
        params.put("customer_id", visita.customer_id);
        params.put("date_visit", visita.date_visit);
        params.put("latitude", visita.latitude);
        params.put("longitude", visita.longitude);
        params.put("notes", visita.notes);
        params.put("state", visita.state != null ? visita.state : "completed");

        if (visita.checklistJson != null && !visita.checklistJson.isEmpty()) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(visita.checklistJson);
                List<Map<String, Object>> checklist = new java.util.ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    Map<String, Object> item = new HashMap<>();
                    item.put("item_id", obj.getInt("item_id"));
                    item.put("checked", obj.getBoolean("checked"));
                    checklist.add(item);
                }
                params.put("checklist", checklist);
            } catch (Exception e) {
                Log.e("SYNC", "No se pudo parsear el checklist de la visita: " + e.getMessage());
            }
        }

        if (visita.signatureBase64 != null && !visita.signatureBase64.isEmpty()) {
            params.put("signature_b64", visita.signatureBase64);
        }

        Map<String, Object> visitaPayload = new HashMap<>();
        visitaPayload.put("params", params);

        try {
            Call<Map<String, Object>> call = apiService.createVisita(authHeader, visitaPayload);
            Response<Map<String, Object>> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                Map<String, Object> jsonResponse = response.body();

                // Odoo (JSON-RPC) encapsula todo en "result"
                Map<String, Object> resultData = (Map<String, Object>) jsonResponse.get("result");

                if (resultData != null) {
                    String errorMsg = (String) resultData.get("error");
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        Log.e("SYNC", "Odoo rechazó la visita lógicamente: " + errorMsg);
                    } else {
                        // Obtenemos el ID de forma segura desde el map de result
                        Object idObj = resultData.get("id");
                        int odooVisitId = idObj instanceof Number ? ((Number) idObj).intValue() : 0;

                        Log.d("SYNC_DEBUG", "ID numérico extraído correctamente de Odoo: " + odooVisitId);

                        if (odooVisitId <= 0) {
                            Log.e("SYNC", "Error crítico: El ID de Odoo es 0 o inválido.");
                            return;
                        }

                        subirMultimediaPendiente(authHeader, visita.external_uuid, odooVisitId);
                        db.visitaDao().marcarComoSincronizada(visita.external_uuid);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SYNC", "Error de red al sincronizar la visita: " + e.getMessage());
        }
    }

    private void sincronizarAsistenciaIndividual(String authHeader, Asistencia asistencia) {
        Map<String, Object> params = new HashMap<>();
        params.put("external_uuid", asistencia.id);
        params.put("date_attendance", asistencia.fecha_hora_str);
        params.put("latitude", asistencia.lat_registro);
        params.put("longitude", asistencia.lon_registro);

        Map<String, Object> payload = new HashMap<>();
        payload.put("params", params);

        try {
            Call<Map<String, Object>> call = apiService.createAsistencia(authHeader, payload);
            Response<Map<String, Object>> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                Map<String, Object> resultData = (Map<String, Object>) response.body().get("result");
                if (resultData != null) {
                    String errorMsg = (String) resultData.get("error");
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        Log.e("SYNC", "Odoo rechazó la asistencia: " + errorMsg);
                    } else {
                        db.asistenciaDao().marcarComoSincronizadas(java.util.Collections.singletonList(asistencia.id));
                        Log.d("SYNC", "Asistencia sincronizada: " + asistencia.id);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SYNC", "Error de red al sincronizar la asistencia: " + e.getMessage());
        }
    }

    private void sincronizarPedidoIndividual(String authHeader, Pedido pedido) {
        List<PedidoLinea> lineas = db.pedidoDao().getLineasPorPedido(pedido.external_uuid);
        if (lineas == null || lineas.isEmpty()) {
            Log.e("SYNC", "Pedido sin líneas, se omite: " + pedido.external_uuid);
            return;
        }

        List<Map<String, Object>> lineasPayload = new java.util.ArrayList<>();
        for (PedidoLinea linea : lineas) {
            Map<String, Object> lineaMap = new HashMap<>();
            lineaMap.put("product_id", linea.productoId);
            lineaMap.put("qty", linea.cantidad);
            lineaMap.put("price_unit", linea.precioUnitario);
            lineasPayload.add(lineaMap);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("external_uuid", pedido.external_uuid);
        params.put("customer_id", pedido.customer_id);
        params.put("date_order", pedido.date_order);
        params.put("notes", pedido.notes);
        params.put("lines", lineasPayload);
        if (pedido.signatureBase64 != null && !pedido.signatureBase64.isEmpty()) {
            params.put("signature_b64", pedido.signatureBase64);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("params", params);

        try {
            Call<Map<String, Object>> call = apiService.createPedido(authHeader, payload);
            Response<Map<String, Object>> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                Map<String, Object> resultData = (Map<String, Object>) response.body().get("result");
                if (resultData != null) {
                    String errorMsg = (String) resultData.get("error");
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        Log.e("SYNC", "Odoo rechazó el pedido: " + errorMsg);
                    } else {
                        db.pedidoDao().marcarComoSincronizado(pedido.external_uuid);
                        Log.d("SYNC", "Pedido sincronizado: " + pedido.external_uuid);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SYNC", "Error de red al sincronizar el pedido: " + e.getMessage());
        }
    }

    private void subirMultimediaPendiente(String authHeader, String externalUuid, int odooVisitId) {
        List<VisitaMultimedia> listaMultimedia = db.visitaDao().getMultimediaPorVisita(externalUuid);
        if (listaMultimedia == null || listaMultimedia.isEmpty()) {
            Log.d("SYNC", "No hay archivos multimedia para la visita UUID: " + externalUuid);
            return;
        }

        Log.d("SYNC", "Subiendo " + listaMultimedia.size() + " archivos multimedia para la visita ID: " + odooVisitId);

        for (VisitaMultimedia multimedia : listaMultimedia) {
            try {
                Uri uri = Uri.parse(multimedia.fileUri);

                // Detección automática del tipo MIME (soporta imágenes y videos)
                String mimeType = context.getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "image/jpeg";

                boolean esVideo = mimeType.startsWith("video");
                String tipoMediaStr = esVideo ? "video" : "image";

                File file = convertirUriAFileTemp(uri, mimeType);

                if (file == null || !file.exists()) {
                    Log.e("SYNC", "No se pudo obtener el archivo físico desde la URI: " + multimedia.fileUri);
                    continue;
                }

                // Comprimimos antes de subir para no saturar el servidor del cliente
                file = esVideo ? mediaCompressor.comprimirVideo(file) : mediaCompressor.comprimirImagen(file);
                if (esVideo) {
                    mimeType = "video/mp4"; // Transformer siempre exporta a MP4/H.264
                }

                RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), file);
                MultipartBody.Part bodyFile = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

                RequestBody mediaType = RequestBody.create(MediaType.parse("text/plain"), tipoMediaStr);
                RequestBody name = RequestBody.create(MediaType.parse("text/plain"), file.getName());
                RequestBody latitude = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(multimedia.latitude));
                RequestBody longitude = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(multimedia.longitude));

                Call<Map<String, Object>> call = apiService.uploadMedia(
                        authHeader, odooVisitId, bodyFile, mediaType, name, latitude, longitude
                );

                Response<Map<String, Object>> response = call.execute();

                if (response.isSuccessful()) {
                    Log.d("SYNC", tipoMediaStr.toUpperCase() + " subida con éxito: " + file.getName());
                } else {
                    String errorBodyStr = response.errorBody() != null ? response.errorBody().string() : "Desconocido";
                    Log.e("SYNC", "Error al subir multimedia a Odoo: " + errorBodyStr);
                }

                file.delete();

            } catch (Exception e) {
                Log.e("SYNC", "Excepción al procesar/subir archivo multimedia: " + e.getMessage());
            }
        }
    }

    private File convertirUriAFileTemp(Uri uri, String mimeType) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            String sufijo = mimeType.startsWith("video") ? ".mp4" : ".jpg";
            File tempFile = File.createTempFile("upload_", sufijo, context.getCacheDir());
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (Exception e) {
            Log.e("SYNC", "Error convirtiendo Uri a File: " + e.getMessage());
            return null;
        }
    }

    private String obtenerTokenGuardado() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE);
        return prefs.getString("auth_token", null);
    }
}