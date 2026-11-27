package com.jazaldcorp.inveragrovendedores.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.Cliente;

@Dao
public interface ClienteDao {
    // El REPLACE hace la magia: si Odoo manda un cliente con el mismo ID pero datos nuevos,
    // Room lo sobreescribe y LiveData avisa a la pantalla.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertClientes(List<Cliente> clientes);

    @Query("SELECT * FROM clientes WHERE id = :clienteId LIMIT 1")
    Cliente getClienteById(int clienteId); // <- Cambiado a int para coincidir con el id de Odoo

    // Al usar LiveData, Room ejecuta esto en segundo plano automáticamente
    @Query("SELECT * FROM clientes")
    LiveData<List<Cliente>> getAllClientesLive();

    // 1. Cuenta cuántos clientes tienes asignados en total
    @Query("SELECT COUNT(*) FROM clientes")
    int getTotalClientesRuta();

    // 2. Cuenta a cuántos clientes ya se les hizo una visita (sin contar duplicados)
    @Query("SELECT COUNT(DISTINCT customer_id) FROM visitas") // Cambiado de cliente_id a customer_id
    int getClientesVisitadosRuta();

    // 3. Busca el primer cliente que AÚN NO esté en la tabla de visitas
    @Query("SELECT * FROM clientes WHERE id NOT IN (SELECT customer_id FROM visitas) LIMIT 1") // Cambiado a customer_id
    Cliente getProximoClientePendiente();

    @Query("SELECT * FROM clientes")
    List<Cliente> getTodosLosClientesParaMapa();

    @Query("SELECT customer_id FROM visitas") // Cambiado a customer_id
    List<Integer> getIdsVisitados(); // Cambiado a List<Integer>
}