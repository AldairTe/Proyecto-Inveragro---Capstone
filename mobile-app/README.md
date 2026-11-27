# App Móvil Android - Inveragro Vendedores

Aplicación móvil nativa en Android (Java) para la fuerza de ventas e ingenieros de campo de Inveragro. Diseñada bajo arquitectura **Offline-First**, permite operar con total autonomía en zonas agrícolas sin señal celular.

## Tecnologías Principales
* **Lenguaje:** Java 17 / Android SDK 34 (Android 14)
* **Persistencia Local:** Android Room ORM (SQLite)
* **Red y API:** Retrofit 2 + OkHttp 4 (JSON-RPC 2.0 sobre HTTP)
* **Geolocalización:** Google Play Services FusedLocationProviderClient + Google Maps SDK
* **Diseño UI:** Material Design 3, ViewBinding, ConstraintLayout, Lottie

## Compilación y Ejecución
1. Configurar `local.properties` con la ruta de tu Android SDK (usar `local.properties.example` como referencia).
2. Compilar con Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
3. El APK se generará en `app/build/outputs/apk/debug/app-debug.apk`.
