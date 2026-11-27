package com.jazaldcorp.inveragrovendedores.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private static final String PREF_NAME = "SessionPrefs"; // Coincide con lo que usamos en los repositorios
    private static final String IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_AUTH_TOKEN = "auth_token"; // Llave para guardar el Token Bearer de Odoo
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_IS_ACTIVATED = "is_activated";
    private static final String KEY_ATTENDANCE_ENABLED = "attendance_enabled";
    private static final String KEY_ATTENDANCE_MAX_PER_DAY = "attendance_max_per_day";
    private static final String KEY_SIGNATURE_ENABLED = "signature_enabled";
    private static final String KEY_BRANDING_APP_NAME = "branding_app_name";
    private static final String KEY_BRANDING_COLOR = "branding_color";
    private static final String KEY_BRANDING_LOGO_B64 = "branding_logo_b64";
    private static final String KEY_LAST_SYNC_MILLIS = "last_sync_millis";
    private static final String KEY_TOKEN_EXPIRES_AT = "token_expires_at";
    private static final String KEY_LOCATION_ENABLED = "location_tracking_enabled";
    private static final String KEY_LOCATION_INTERVAL = "location_tracking_interval_seconds";
    private static final String KEY_GEOFENCE_RADIUS = "location_geofence_radius_meters";
    private static final String KEY_LAST_GEOFENCE_NOTIFIED_DATE = "last_geofence_notified_date";

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // Guarda la URL del Odoo del cliente tras validar el código de activación.
    // Se normaliza para que siempre termine en "/", como lo requiere Retrofit.baseUrl().
    public void saveServerConfig(String serverUrl) {
        String normalizada = serverUrl.trim();
        if (!normalizada.endsWith("/")) {
            normalizada = normalizada + "/";
        }
        editor.putString(KEY_SERVER_URL, normalizada);
        editor.putBoolean(KEY_IS_ACTIVATED, true);
        editor.apply();
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, null);
    }

    public boolean isActivated() {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false) && getServerUrl() != null;
    }

    // Cachea la configuración de asistencia que trae Odoo, para que la app siga
    // funcionando offline con el último valor conocido tras el primer fetch exitoso.
    public void saveAttendanceConfig(boolean enabled, int maxPerDay) {
        editor.putBoolean(KEY_ATTENDANCE_ENABLED, enabled);
        editor.putInt(KEY_ATTENDANCE_MAX_PER_DAY, maxPerDay);
        editor.apply();
    }

    // Por defecto (antes del primer fetch) se asume habilitada con 1 marcación diaria,
    // que era el comportamiento original de la app.
    public boolean isAttendanceEnabled() {
        return prefs.getBoolean(KEY_ATTENDANCE_ENABLED, true);
    }

    public int getAttendanceMaxPerDay() {
        return prefs.getInt(KEY_ATTENDANCE_MAX_PER_DAY, 1);
    }

    public void saveSignatureConfig(boolean enabled) {
        editor.putBoolean(KEY_SIGNATURE_ENABLED, enabled);
        editor.apply();
    }

    // Por defecto deshabilitada hasta que se confirme lo contrario con el servidor
    // (coincide con el default en Odoo: active_feature = False).
    public boolean isSignatureEnabled() {
        return prefs.getBoolean(KEY_SIGNATURE_ENABLED, false);
    }

    public void saveBranding(String appName, String colorHex, String logoBase64) {
        editor.putString(KEY_BRANDING_APP_NAME, appName);
        editor.putString(KEY_BRANDING_COLOR, colorHex);
        editor.putString(KEY_BRANDING_LOGO_B64, logoBase64);
        editor.apply();
    }

    public String getBrandingAppName() {
        return prefs.getString(KEY_BRANDING_APP_NAME, null);
    }

    public String getBrandingColor() {
        return prefs.getString(KEY_BRANDING_COLOR, null);
    }

    public String getBrandingLogoBase64() {
        return prefs.getString(KEY_BRANDING_LOGO_B64, null);
    }

    public void saveUltimaSincronizacionAhora() {
        editor.putLong(KEY_LAST_SYNC_MILLIS, System.currentTimeMillis());
        editor.apply();
    }

    /** -1 si nunca se ha sincronizado en este dispositivo. */
    public long getUltimaSincronizacionMillis() {
        return prefs.getLong(KEY_LAST_SYNC_MILLIS, -1);
    }

    public void saveTokenExpiresAt(String expiresAt) {
        editor.putString(KEY_TOKEN_EXPIRES_AT, expiresAt);
        editor.apply();
    }

    public String getTokenExpiresAt() {
        return prefs.getString(KEY_TOKEN_EXPIRES_AT, null);
    }

    public void saveLocationTrackingConfig(boolean enabled, int intervalSeconds, int geofenceRadiusMeters) {
        editor.putBoolean(KEY_LOCATION_ENABLED, enabled);
        editor.putInt(KEY_LOCATION_INTERVAL, intervalSeconds);
        editor.putInt(KEY_GEOFENCE_RADIUS, geofenceRadiusMeters);
        editor.apply();
    }

    // Desactivado por defecto hasta confirmar con el servidor (mismo default que en Odoo).
    public boolean isLocationTrackingEnabled() {
        return prefs.getBoolean(KEY_LOCATION_ENABLED, false);
    }

    public int getLocationTrackingIntervalSeconds() {
        return prefs.getInt(KEY_LOCATION_INTERVAL, 60);
    }

    public int getGeofenceRadiusMeters() {
        return prefs.getInt(KEY_GEOFENCE_RADIUS, 150);
    }

    // Evita re-notificar al vendedor por el mismo cliente varias veces el mismo día.
    public boolean yaNotificoGeocercaHoy(int customerId, String hoyStr) {
        return prefs.getBoolean(KEY_LAST_GEOFENCE_NOTIFIED_DATE + "_" + customerId + "_" + hoyStr, false);
    }

    public void marcarGeocercaNotificada(int customerId, String hoyStr) {
        editor.putBoolean(KEY_LAST_GEOFENCE_NOTIFIED_DATE + "_" + customerId + "_" + hoyStr, true);
        editor.apply();
    }

    // Guarda la sesión y el Token Bearer cuando el login en Odoo es exitoso
    public void createSession(String token) {
        editor.putBoolean(IS_LOGGED_IN, true);
        editor.putString(KEY_AUTH_TOKEN, token);
        editor.apply();
    }

    // Verifica si ya hay una sesión activa
    public boolean isLoggedIn() {
        return prefs.getBoolean(IS_LOGGED_IN, false);
    }

    // Recupera el token guardado para enviarlo en los headers de Retrofit
    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    // Para cerrar sesión: limpia el usuario/token pero mantiene la activación
    // (servidor + código), ya que esa configuración es del dispositivo, no del usuario.
    public void logout() {
        editor.remove(IS_LOGGED_IN);
        editor.remove(KEY_AUTH_TOKEN);
        editor.apply();
    }
}