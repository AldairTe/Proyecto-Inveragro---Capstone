# Backend ERP - Inveragro Field Sales Mobile

Este directorio contiene la infraestructura en contenedores (Docker) y el módulo personalizado de Odoo 13 (`field_sales_mobile`) para la gestión comercial, asignación de rutas, supervisión de telemetría y sincronización con la app móvil.

## Estructura del Directorio
* `docker-compose.yml`: Define los servicios `odoo` (Odoo 13 Community) y `db` (PostgreSQL 10).
* `config/odoo.conf`: Configuración del servidor Odoo, rutas de addons y parámetros de conexión.
* `addons/field_sales_mobile/`: Módulo de Odoo que implementa la lógica de negocio, endpoints JSON-RPC, modelos ORM y vistas.

## Puesta en Marcha Rápida
```bash
# Iniciar servicios en segundo plano
docker-compose up -d

# Ver logs de Odoo
docker logs -f odoo13

# Detener servicios
docker-compose stop
```
Acceso Web: [http://localhost:8069](http://localhost:8069)
