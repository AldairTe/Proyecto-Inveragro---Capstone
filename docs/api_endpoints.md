# Catálogo de Endpoints de la API - Field Sales Mobile

La comunicación entre la aplicación móvil Android y el servidor backend Odoo 13 se realiza mediante el protocolo **JSON-RPC 2.0 sobre HTTP POST**.

---

## 1. Autenticación y Activación

### `POST /field_sales/api/activate`
Activa el dispositivo móvil de un vendedor mediante un código de activación único generado desde Odoo.
* **Autenticación:** Pública (`auth='none'`)
* **Payload:**
```json
{
  "params": {
    "activation_code": "A1B2C3D4"
  }
}
```
* **Respuesta exitosa:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "success": true,
    "company_name": "Inveragro S.A.C.",
    "branding_color": "#2E7D32",
    "logo_base64": "..."
  }
}
```

### `POST /field_sales/api/login`
Autenticación de vendedores de campo y expedición del Bearer Token de sesión.
* **Autenticación:** Pública (`auth='none'`)
* **Payload:**
```json
{
  "params": {
    "login": "V0001",
    "password": "mi_password_seguro"
  }
}
```
* **Respuesta exitosa:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "token": "token_urlsafe_32_caracteres_aleatorio",
    "expires_at": "2026-12-31 23:59:59",
    "employee": {
      "id": 2,
      "seller_code": "V0001",
      "name": "Juan Pérez",
      "sales_zone": "Zona Norte - Valles de Ica"
    }
  }
}
```

---

## 2. Clientes y Catálogo de Productos

### `POST /field_sales/api/customers`
Obtiene la cartera de clientes y fundos asignados al vendedor autenticado.
* **Headers:** `Authorization: Bearer <token>`
* **Respuesta:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "customers": [
      {
        "id": 1,
        "name": "Fundo Santa María",
        "contact_name": "Ing. Carlos Mendoza",
        "phone": "+51 956 123 456",
        "latitude": -11.827022,
        "longitude": -77.166591
      }
    ]
  }
}
```

### `POST /field_sales/api/catalog`
Sincroniza el catálogo de productos agroquímicos, fertilizantes y semillas con precios y stock actualizado.
* **Headers:** `Authorization: Bearer <token>`

---

## 3. Planificación de Rutas y Visitas

### `POST /field_sales/api/route/today`
Obtiene la secuencia de paradas planificadas para el día de trabajo del vendedor.
* **Headers:** `Authorization: Bearer <token>`

### `POST /field_sales/api/visits/create`
Registra una visita de campo con validación de geocerca geodésica (fórmula de Haversine), notas y estado de sincronización.
* **Headers:** `Authorization: Bearer <token>`
* **Payload:**
```json
{
  "params": {
    "external_uuid": "e5c2b3d1-4f9a-4e2b-98a7-7b8c9d0e1f2a",
    "customer_id": 1,
    "date_visit": "2026-11-27 10:30:00",
    "latitude": -11.827022,
    "longitude": -77.166591,
    "notes": "Revisión fitosanitaria de cultivo de uva de mesa."
  }
}
```

### `POST /field_sales/api/visits/<int:visit_id>/media`
Adjunta evidencias fotográficas y archivos multimedia a la visita registrada.
* **Form-Data multipart:** `file`, `title`, `type`

---

## 4. Pedidos de Venta de Campo (Preventa)

### `POST /field_sales/api/orders/create`
Genera un pedido de venta en estado borrador (`draft`) en Odoo con sus líneas correspondientes.
* **Headers:** `Authorization: Bearer <token>`
* **Payload:**
```json
{
  "params": {
    "external_uuid": "ord-uuid-2026",
    "customer_id": 1,
    "date_order": "2026-11-27 10:45:00",
    "order_lines": [
      {
        "product_id": 101,
        "quantity": 10,
        "unit_price": 135.00
      }
    ]
  }
}
```

---

## 5. Telemetría y Rastreo en Vivo

### `POST /field_sales/api/location/ping`
Recepción de pings periódicos de telemetría GPS del vendedor en campo.
* **Headers:** `Authorization: Bearer <token>`
* **Payload:**
```json
{
  "params": {
    "latitude": -11.827022,
    "longitude": -77.166591,
    "accuracy": 4.5,
    "captured_at": "2026-11-27 10:35:00",
    "battery_level": 85
  }
}
```

### `GET /field_sales/live_map`
Panel web responsivo con mapa interactivo de supervisión satelital de vendedores en tiempo real.
