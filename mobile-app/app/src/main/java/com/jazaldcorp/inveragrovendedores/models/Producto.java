package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "productos")
public class Producto {
    @PrimaryKey
    @NonNull
    @SerializedName("id")
    public Integer id;

    @SerializedName("name")
    public String nombre;

    @SerializedName("default_code")
    public String codigo;

    @SerializedName("uom_name")
    public String unidadMedida;

    @SerializedName("price")
    public double precio;

    @SerializedName("qty_available")
    public Double stockDisponible; // null = la empresa no muestra stock (show_stock=false en Odoo)

    public Producto() {}
}
