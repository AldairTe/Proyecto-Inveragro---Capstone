package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "clientes")
public class Cliente {
    @PrimaryKey
    @NonNull
    @SerializedName("id")
    public Integer id;

    @SerializedName("name")
    public String nombre_fundo;

    @SerializedName("contact_name")
    public String nombre_contacto;

    @SerializedName("phone")
    public String telefono;

    @SerializedName("latitude")
    public double latitud;

    @SerializedName("longitude")
    public double longitud;

    @SerializedName("external_uuid")
    public String external_uuid; // Corregido para coincidir exactamente con el campo de Odoo

    public transient String distanciaCalculada;

    public Cliente() {}
}