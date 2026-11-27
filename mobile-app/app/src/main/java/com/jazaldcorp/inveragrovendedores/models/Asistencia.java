package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "asistencias")
public class Asistencia {
    @PrimaryKey
    @NonNull
    @SerializedName("external_uuid")
    public String id; // El UUID generado en Android (clave de idempotencia offline)

    @SerializedName("employee_id")
    public int usuario_id; // Cambiado a int para coincidir con el id del usuario/empleado en Odoo

    @SerializedName("date_attendance")
    public String fecha_hora_str; // Odoo maneja fechas en formato texto "YYYY-MM-DD HH:MM:SS"

    public long fecha_hora; // Mantenemos tu campo local en milisegundos si lo usas en Room para consultas de hoy

    @SerializedName("latitude")
    public double lat_registro;

    @SerializedName("longitude")
    public double lon_registro;

    public boolean sincronizado; // false = offline, true = subido a Odoo

    public Asistencia() {}
}