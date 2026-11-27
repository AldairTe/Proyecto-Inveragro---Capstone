package com.jazaldcorp.inveragrovendedores.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.Visita;
import com.jazaldcorp.inveragrovendedores.models.VisitaMultimedia;

@Dao
public interface VisitaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVisita(Visita visita);

    // Para el worker o servicio que subirá los datos cuando vuelva el internet
    @Query("SELECT * FROM visitas WHERE sincronizado = false")
    List<Visita> getVisitasNoSincronizadas();

    // El cliente_id ahora es un entero (int) en el modelo Visita
    @Query("SELECT * FROM visitas WHERE customer_id = :clienteId ORDER BY date_visit DESC")
    List<Visita> getVisitasPorCliente(int clienteId);

    // Como la clave primaria ahora es external_uuid, actualizamos por ese campo
    @Query("UPDATE visitas SET sincronizado = true WHERE external_uuid = :externalUuid")
    void marcarComoSincronizada(String externalUuid);


    @Query("SELECT COUNT(*) FROM visitas")
    int getTotalVisitasTotales();

    @Query("SELECT COUNT(*) FROM visitas WHERE sincronizado = false")
    int getTotalVisitasPendientes();

    @Query("SELECT COUNT(*) FROM visitas WHERE fecha_hora >= :inicioDeHoy")
    int getVisitasHoy(long inicioDeHoy);

    @androidx.room.Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMultimediaList(List<VisitaMultimedia> multimediaList);

    @Query("SELECT * FROM visita_multimedia WHERE visitaUuid = :visitaUuid")
    List<VisitaMultimedia> getMultimediaPorVisita(String visitaUuid);

}