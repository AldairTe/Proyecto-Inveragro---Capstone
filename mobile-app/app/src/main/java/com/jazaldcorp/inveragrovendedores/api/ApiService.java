package com.jazaldcorp.inveragrovendedores.api;

import com.jazaldcorp.inveragrovendedores.models.VisitaResponse;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface ApiService {

    // 0. Activación inicial del dispositivo (código generado en Odoo > Configuración)
    @POST("field_sales/api/activate")
    Call<Map<String, Object>> activate(@Body Map<String, Object> bodyJsonRpc);

    // 1. Endpoint de Login (POST) - Modificado para recibir la estructura JSON-RPC completa
    @POST("field_sales/api/login")
    Call<Map<String, Object>> login(@Body Map<String, Object> bodyJsonRpc);

    // 2. Endpoint para descargar los clientes asignados (GET)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/customers")
    Call<Map<String, Object>> getClientes(
            @Header("Authorization") String token,
            @Body Object body // Cuerpo vacío o con parámetros que exige JSON-RPC de Odoo
    );

    // 3. Endpoint para obtener el historial de visitas (POST para soportar JSON-RPC y filtros opcionales como customer_id)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/visits")
    Call<Map<String, Object>> getVisitas(
            @Header("Authorization") String token,
            @Body Map<String, Object> bodyJsonRpc
    );

    // Cambiamos VisitaResponse por Map<String, Object> para leer el JSON-RPC completo
    @POST("field_sales/api/visits/create")
    Call<Map<String, Object>> createVisita(
            @Header("Authorization") String token,
            @Body Map<String, Object> visitaPayload
    );

    // 5. Configuración de asistencia (habilitada/deshabilitada, marcaciones permitidas por día)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/attendance/config")
    Call<Map<String, Object>> getAttendanceConfig(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 5.1 Listar pedidos (opcionalmente filtrados por customer_id) para el historial rápido del cliente
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/orders")
    Call<Map<String, Object>> getPedidosRemotos(
            @Header("Authorization") String token,
            @Body Map<String, Object> bodyJsonRpc
    );

    // 6. Registrar una marcación de asistencia
    @POST("field_sales/api/attendance/create")
    Call<Map<String, Object>> createAsistencia(
            @Header("Authorization") String token,
            @Body Map<String, Object> bodyJsonRpc
    );

    // 7. Catálogo de productos (filtrado por categoría/lista de precios según Odoo > Configuración)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/catalog")
    Call<Map<String, Object>> getCatalogo(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 8. Crear un pedido (queda en field.sales.order dentro de Odoo, no toca sale.order directamente)
    @POST("field_sales/api/orders/create")
    Call<Map<String, Object>> createPedido(
            @Header("Authorization") String token,
            @Body Map<String, Object> bodyJsonRpc
    );

    // 9. Ítems de checklist agronómico configurados en Odoo
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/checklist/items")
    Call<Map<String, Object>> getChecklistItems(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 10. Configuración de firma digital (habilitada/deshabilitada)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/signature/config")
    Call<Map<String, Object>> getSignatureConfig(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 11. Ruta planificada para hoy (si la empresa usa esta función)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/route/today")
    Call<Map<String, Object>> getRutaHoy(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 12. Marca/branding de la empresa (público, sin token)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/branding")
    Call<Map<String, Object>> getBranding(@Body Object body);

    // 13. Configuración de ubicación en vivo (habilitada, intervalo de envío)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/location/config")
    Call<Map<String, Object>> getLocationConfig(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 14. Enviar un ping de ubicación
    @POST("field_sales/api/location/ping")
    Call<Map<String, Object>> createLocationPing(
            @Header("Authorization") String token,
            @Body Map<String, Object> bodyJsonRpc
    );

    // 14.1 Ver mi propia ruta de hoy (transparencia para el vendedor)
    @Headers({
            "Accept: application/json",
            "Content-Type: application/json"
    })
    @POST("field_sales/api/location/my_route")
    Call<Map<String, Object>> getMiRutaHoy(
            @Header("Authorization") String token,
            @Body Object body
    );

    // 15. Endpoint para subir evidencias (Fotos / Videos) por Multipart
    @Multipart
    @POST("field_sales/api/visits/{visit_id}/media")
    Call<Map<String, Object>> uploadMedia(
            @Header("Authorization") String token,
            @Path("visit_id") int visitId,
            @Part MultipartBody.Part file,
            @Part("media_type") RequestBody mediaType,
            @Part("name") RequestBody name,
            @Part("latitude") RequestBody latitude,
            @Part("longitude") RequestBody longitude
    );
}