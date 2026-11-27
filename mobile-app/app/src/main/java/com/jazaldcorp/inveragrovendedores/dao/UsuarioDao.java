package com.jazaldcorp.inveragrovendedores.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.jazaldcorp.inveragrovendedores.models.Usuario;

@Dao
public interface UsuarioDao {

    // Guarda el usuario que se loguea, si ya existe lo reemplaza (útil para actualizar datos)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUsuario(Usuario usuario);

    // Para el login offline (busca por dni/seller_code)
    @Query("SELECT * FROM usuarios WHERE dni = :dni LIMIT 1")
    Usuario loginLocal(String dni);

    // Borra la tabla al cerrar sesión
    @Query("DELETE FROM usuarios")
    void deleteAll();

    @Query("SELECT * FROM usuarios LIMIT 1")
    Usuario getUsuarioLogueado();
}