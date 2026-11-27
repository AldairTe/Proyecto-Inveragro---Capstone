package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pedido_lineas")
public class PedidoLinea {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String pedidoUuid; // Relación con Pedido.external_uuid

    public int productoId;
    public String productoNombre; // Snapshot para mostrar en la lista sin hacer join
    public double cantidad;
    public double precioUnitario;

    public PedidoLinea() {}

    public PedidoLinea(@NonNull String pedidoUuid, int productoId, String productoNombre, double cantidad, double precioUnitario) {
        this.pedidoUuid = pedidoUuid;
        this.productoId = productoId;
        this.productoNombre = productoNombre;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }
}
