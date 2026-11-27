# Guía de Instalación y Despliegue Local

Esta guía detalla los pasos para levantar el entorno de desarrollo completo del proyecto **Inveragro (Backend Odoo + App Android)** en una máquina local o servidor de pruebas.

---

## 1. Requisitos Previos

* **Git:** Versión 2.30 o superior.
* **Docker & Docker Compose:** Docker Desktop (Windows/macOS) o Docker Engine (Linux).
* **Android Studio:** Ladybug (2024.2+) o superior, con Android SDK Build-Tools 34.
* **JDK:** Java 17 o el JBR incluido con Android Studio.

---

## 2. Despliegue del Backend (Odoo 13 + PostgreSQL)

1. **Abrir una terminal y navegar al directorio del backend:**
   ```bash
   cd backend
   ```

2. **Levantar los contenedores de Docker:**
   ```bash
   docker-compose up -d
   ```

3. **Verificar que los servicios estén activos:**
   ```bash
   docker ps
   ```
   * El contenedor `odoo13` estará escuchando en el puerto `8069`.
   * El contenedor `odoo13-db` estará escuchando internamente en el puerto `5432`.

4. **Acceder a la interfaz web de Odoo:**
   * Abrir en el navegador: [http://localhost:8069](http://localhost:8069)
   * Base de datos: `postgres` o la base que inicialices.
   * Usuario administrador: `admin` / Contraseña: `admin`

5. **Instalar / Actualizar el módulo `field_sales_mobile`:**
   * En Odoo, ir a **Ajustes > Activar modo desarrollador**.
   * Ir a **Aplicaciones > Actualizar lista de aplicaciones**.
   * Buscar `field_sales_mobile` (o "Inveragro Ventas Móviles") y pulsar **Instalar**.

---

## 3. Configuración y Compilación de la App Android

1. **Abrir el proyecto en Android Studio:**
   * Seleccionar **File > Open...** y elegir la carpeta `mobile-app`.
   * Esperar a que Gradle sincronice las dependencias del proyecto.

2. **Configurar el archivo local del SDK:**
   * Copiar `mobile-app/local.properties.example` a `mobile-app/local.properties`.
   * Asegurarse de que `sdk.dir` apunte a la ruta de tu Android SDK.

3. **Compilar el archivo APK en modo Debug:**
   * Desde Android Studio: Menú **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   * O por línea de comandos:
     ```bash
     cd mobile-app
     ./gradlew assembleDebug
     ```
   * El APK resultante se generará en:
     `mobile-app/app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Conexión entre la App Móvil y el Servidor Odoo

| Escenario de Prueba | URL a ingresar en la App (Pantalla de Activación) |
| :--- | :--- |
| **Emulador Android Oficial (AVD)** | `http://10.0.2.2:8069` *(10.0.2.2 es el alias del host en el emulador)* |
| **Celular Físico (mismo Wi-Fi)** | `http://<IP_LOCAL_DE_TU_PC>:8069` *(Ejemplo: `http://192.168.1.3:8069`)* |
| **Servidor en la Nube / VPS** | `https://tudominio.com` |
