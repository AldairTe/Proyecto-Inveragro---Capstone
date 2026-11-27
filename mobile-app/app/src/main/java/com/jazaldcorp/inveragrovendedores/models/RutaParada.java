package com.jazaldcorp.inveragrovendedores.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Caché local de la ruta planificada para HOY (se reemplaza completa en cada sincronización). */
@Entity(tableName = "ruta_paradas")
public class RutaParada {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int customerId;
    public String customerName;
    public int secuencia;
    public boolean visitado;

    public RutaParada() {}
}
