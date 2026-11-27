package com.jazaldcorp.inveragrovendedores.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.RutaParada;

@Dao
public interface RutaParadaDao {
    @Insert
    void insertParadas(List<RutaParada> paradas);

    @Query("DELETE FROM ruta_paradas")
    void borrarTodas();

    @Query("SELECT * FROM ruta_paradas ORDER BY secuencia")
    List<RutaParada> getParadasDeHoy();

    @Query("SELECT COUNT(*) FROM ruta_paradas")
    int getTotalParadas();

    @Query("SELECT COUNT(*) FROM ruta_paradas WHERE visitado = 1")
    int getParadasVisitadas();

    @Query("SELECT * FROM ruta_paradas WHERE visitado = 0 ORDER BY secuencia LIMIT 1")
    RutaParada getProximaParada();
}
