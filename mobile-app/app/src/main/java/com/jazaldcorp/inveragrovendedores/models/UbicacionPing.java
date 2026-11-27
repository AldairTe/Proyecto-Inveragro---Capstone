package com.jazaldcorp.inveragrovendedores.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cola local de ubicaciones capturadas por LocationTrackingService. Se intenta enviar cada
 * una de inmediato; si falla (sin señal, típico en campo), queda aquí para que
 * SincronizacionRepository la reintente más tarde, igual que visitas/pedidos/asistencias.
 */
@Entity(tableName = "ubicacion_pings")
public class UbicacionPing {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public double latitude;
    public double longitude;
    public float accuracy;
    public int batteryLevel = -1; // -1 = desconocido
    public String capturedAtStr; // Formato Odoo "yyyy-MM-dd HH:mm:ss"
    public boolean sincronizado;

    public UbicacionPing() {}
}
