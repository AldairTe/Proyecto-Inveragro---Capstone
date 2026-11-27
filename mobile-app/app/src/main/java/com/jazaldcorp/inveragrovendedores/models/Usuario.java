package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "usuarios")
public class Usuario {

    @PrimaryKey
    @NonNull
    @SerializedName("id")
    public int id; // En Odoo el ID del empleado es un número entero

    @SerializedName("seller_code")
    public String dni; // O código de vendedor que devuelve el backend

    @SerializedName("name")
    public String nombre_completo;

    @SerializedName("sales_zone")
    public String zonaVenta; // Extra útil que devuelve Odoo

    // Se persiste (ya no es transient) para poder restaurar la sesión en el login offline:
    // si no hay internet, se reutiliza el último token conocido hasta volver a tener conexión.
    public String token;

    // Hash SHA-256 de "dni:password" calculado tras el último login exitoso ONLINE.
    // Permite verificar la contraseña sin conexión, sin guardar la contraseña en texto plano.
    public String passwordHashLocal;

    public boolean estado;

    // Constructor vacío requerido por Room
    public Usuario() {}

    // Constructor con parámetros
    public Usuario(@NonNull int id, String dni, String nombre_completo, boolean estado) {
        this.id = id;
        this.dni = dni;
        this.nombre_completo = nombre_completo;
        this.estado = estado;
    }
}