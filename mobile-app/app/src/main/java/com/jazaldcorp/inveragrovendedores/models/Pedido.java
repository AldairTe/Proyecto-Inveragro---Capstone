package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "pedidos")
public class Pedido {
    @PrimaryKey
    @NonNull
    @SerializedName("external_uuid")
    public String external_uuid; // UUID generado en Android (clave de idempotencia offline)

    @SerializedName("customer_id")
    public int customer_id;

    @SerializedName("employee_id")
    public int employee_id;

    @SerializedName("date_order")
    public String date_order; // Formato Odoo "YYYY-MM-DD HH:MM:SS"

    @SerializedName("notes")
    public String notes;

    public long fecha_hora; // Timestamp local en milisegundos, para ordenar/consultar en Room

    public double montoTotal; // Snapshot del total calculado al momento de guardar (para listar sin joins)

    public boolean sincronizado;

    public String signatureBase64; // Firma del cliente en PNG/Base64 (null si no se capturó)

    public Pedido() {}
}
