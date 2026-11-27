# Proyecto Inveragro - Sistema de Preventa Agrícola, Geocercas y Telemetría en Campo

[![Android](https://img.shields.io/badge/Android-Native%20Java-3DDC84?logo=android&logoColor=white)](mobile-app/)
[![Odoo](https://img.shields.io/badge/Odoo-13.0%20Community-714B67?logo=odoo&logoColor=white)](backend/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker&logoColor=white)](backend/docker-compose.yml)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-10-4169E1?logo=postgresql&logoColor=white)](backend/)
[![Architecture](https://img.shields.io/badge/Architecture-Offline--First%20%7C%20Room%20SQLite-blue)](docs/arquitectura.md)


## 🏛️ Arquitectura General

```mermaid
flowchart TD
    subgraph Campo["Operación en Campo (Offline)"]
        Vendedor["Vendedor de Campo"]
        App["App Móvil Android"]
        RoomDB[("SQLite Local Room")]
        GPS["Sensor GPS"]
        
        Vendedor --> App
        GPS --> App
        App --> RoomDB
    end

    subgraph Sync["Motor de Sincronización"]
        SyncWorker["Sync Repository OkHttp"]
        RoomDB --> SyncWorker
    end

    subgraph Central["Servidor Central (Docker)"]
        Nginx["Gateway Nginx"]
        OdooAPI["Odoo 13 JSON-RPC"]
        ModSales["Módulo field_sales_mobile"]
        PostgresDB[("Base de Datos PostgreSQL 10")]
        DashboardWeb["Supervisión Web Live Map"]

        SyncWorker -.-> Nginx
        Nginx --> OdooAPI
        OdooAPI --> ModSales
        ModSales --> PostgresDB
        DashboardWeb --> ModSales
    end
```

---

## 📂 Estructura del Repositorio

El repositorio unifica el ecosistema completo del proyecto en dos capas modulares:

```
Proyecto-Inveragro---Capstone/
│
├── backend/                             # Servidor Backend ERP (Docker & Odoo)
│   ├── docker-compose.yml               # Orquestación de servicios Odoo 13 y PostgreSQL 10
│   ├── config/                          # Archivos de configuración de Odoo (odoo.conf)
│   └── addons/
│       └── field_sales_mobile/          # Módulo custom de Odoo (Modelos, Vistas, API JSON-RPC)
│           ├── controllers/             # Enrutadores de endpoints para la app móvil
│           ├── models/                  # Modelos de datos (visitas, pedidos, telemetría, clientes)
│           ├── views/                   # Vistas XML y mapa satelital en vivo
│           └── security/                # Reglas de acceso y perfiles de usuario
│
├── mobile-app/                          # Aplicación Móvil Android Nativa
│   ├── build.gradle                     # Configuración de compilación raíz
│   ├── local.properties.example         # Plantilla para configuración del SDK local
│   └── app/
│       ├── build.gradle                 # Dependencias (Room, Retrofit, Maps, FusedLocation)
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml  # Permisos, servicios y metadata de la app
│           │   ├── java/                # Código fuente Java (Activities, Fragments, DAOs, Repos)
│           │   └── res/                 # Layouts XML, estilos, paleta de colores y recursos
│           └── test/                    # Suite de pruebas unitarias
│
├── docs/                                # Documentación Técnica y de Ingeniería
│   ├── arquitectura.md                  # Descripción detallada de la arquitectura de software
│   ├── api_endpoints.md                 # Contrato completo de la API JSON-RPC
│   └── guia_instalacion.md              # Paso a paso para levantar el entorno local
│
├── .gitignore                           # Exclusión de compilados, caches y datos locales
└── README.md                            # Documentación principal del proyecto
```

---

## 🚀 Puesta en Marcha Rápida

### 1. Iniciar el Backend (Docker)
```bash
cd backend
docker-compose up -d
```
* Servidor Odoo disponible en: [http://localhost:8069](http://localhost:8069)
* Credenciales por defecto de desarrollo: Usuario `admin` | Password `admin`

### 2. Compilar la Aplicación Móvil (Android)
```bash
cd mobile-app
./gradlew assembleDebug
```
* El APK compilado se encontrará en `mobile-app/app/build/outputs/apk/debug/app-debug.apk`.

> Para instrucciones detalladas de configuración de emuladores y conexión Wi-Fi, consulta la [Guía de Instalación](docs/guia_instalacion.md).

---

## 🔒 Seguridad y Privacidad de la Información
Este repositorio fue auditado y preparado conforme a las buenas prácticas de seguridad para repositorios públicos y académicos:
* **Sin Credenciales Expuestas:** No se incluyen certificados de firma de producción (`.jks`/`.keystore`), credenciales corporativas ni API keys privadas en el código fuente.
* **Google Maps API Key:** La clave de mapas se referencia mediante el recurso `@string/google_maps_key` en `strings.xml`, permitiendo al desarrollador inyectar su propia credencial de Google Cloud sin comprometer secretos en el repositorio.
* **Cifrado Criptográfico:** Contraseñas hasheadas en base de datos (`PBKDF2/SHA-256`) y tokens de sesión móviles generados de forma pseudoaleatoria criptográficamente segura (`secrets.token_urlsafe`).
* **Plantillas Seguras:** Configuraciones locales de SDK y variables de entorno se suministran como archivos de ejemplo (`.example`).

---

## 👨‍💻 Autores
* **Aldair TeLLO** - Desarrollo e Integración
