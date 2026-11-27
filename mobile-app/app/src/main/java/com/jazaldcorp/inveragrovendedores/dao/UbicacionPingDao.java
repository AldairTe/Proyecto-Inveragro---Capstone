package com.jazaldcorp.inveragrovendedores.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.UbicacionPing;

@Dao
public interface UbicacionPingDao {
    @Insert
    long insertPing(UbicacionPing ping);

    @Query("SELECT * FROM ubicacion_pings WHERE sincronizado = 0 ORDER BY id ASC LIMIT 50")
    List<UbicacionPing> getPingsPendientes();

    @Query("UPDATE ubicacion_pings SET sincronizado = 1 WHERE id = :id")
    void marcarComoSincronizado(int id);

    // Evita que la tabla crezca indefinidamente si el dispositivo pasa mucho tiempo offline
    @Query("DELETE FROM ubicacion_pings WHERE sincronizado = 1")
    void borrarSincronizados();
}
