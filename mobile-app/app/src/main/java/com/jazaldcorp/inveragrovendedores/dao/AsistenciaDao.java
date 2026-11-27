package com.jazaldcorp.inveragrovendedores.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.Asistencia;

@Dao
public interface AsistenciaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAsistencia(Asistencia asistencia);

    // Esto es clave: Obtiene solo lo que falta subir al servidor de Odoo
    @Query("SELECT * FROM asistencias WHERE sincronizado = 0")
    List<Asistencia> getAsistenciasPendientes();

    // Actualiza el estado a sincronizado una vez que Odoo responde con éxito
    @Query("UPDATE asistencias SET sincronizado = 1 WHERE id IN (:ids)")
    void marcarComoSincronizadas(List<String> ids);

    // El parámetro usuarioId ahora es int para coincidir con el modelo Asistencia y Usuario de Odoo
    @Query("SELECT COUNT(*) FROM asistencias WHERE usuario_id = :usuarioId AND fecha_hora >= :timestampInicioDia")
    int verificarAsistenciaHoy(int usuarioId, long timestampInicioDia);
}