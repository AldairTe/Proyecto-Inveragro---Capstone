package com.jazaldcorp.inveragrovendedores.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.Pedido;
import com.jazaldcorp.inveragrovendedores.models.PedidoLinea;

@Dao
public interface PedidoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPedido(Pedido pedido);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLineas(List<PedidoLinea> lineas);

    @Query("SELECT * FROM pedidos WHERE sincronizado = 0")
    List<Pedido> getPedidosNoSincronizados();

    @Query("SELECT * FROM pedido_lineas WHERE pedidoUuid = :pedidoUuid")
    List<PedidoLinea> getLineasPorPedido(String pedidoUuid);

    @Query("UPDATE pedidos SET sincronizado = 1 WHERE external_uuid = :externalUuid")
    void marcarComoSincronizado(String externalUuid);

    @Query("SELECT * FROM pedidos WHERE customer_id = :clienteId ORDER BY fecha_hora DESC")
    List<Pedido> getPedidosPorCliente(int clienteId);
}
