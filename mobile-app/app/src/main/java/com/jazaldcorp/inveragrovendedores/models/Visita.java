package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "visitas")
public class Visita {
    @PrimaryKey
    @NonNull
    @SerializedName("external_uuid")
    public String external_uuid; // El UUID generado en Android (clave para la idempotencia)

    @SerializedName("customer_id")
    public int customer_id;      // En Odoo el ID del cliente es un entero (int)

    @SerializedName("employee_id")
    public int employee_id;      // Cambiado a int para coincidir con el ID del empleado en Odoo

    @SerializedName("date_visit")
    public String date_visit;    // Fecha en formato texto "YYYY-MM-DD HH:MM:SS" (compatible con Odoo)

    @SerializedName("latitude")
    public double latitude;      // Coordenada de latitud

    @SerializedName("longitude")
    public double longitude;     // Coordenada de longitud

    @SerializedName("notes")
    public String notes;         // Observaciones

    @SerializedName("state")
    public String state;         // Estado (ej. "completed")

    public long fecha_hora;      // Tu campo local en milisegundos para ordenar o filtrar en Room (ej: RecyclerView)

    public boolean sincronizado; // Bandera local para saber si ya subió a Odoo

    public String checklistJson; // Respuestas del checklist agronómico, serializadas como JSON: [{"item_id":1,"checked":true}, ...]

    public String signatureBase64; // Firma del cliente en PNG/Base64 (null si no se capturó)

    public Visita() {}
}