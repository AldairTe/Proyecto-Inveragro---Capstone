package com.jazaldcorp.inveragrovendedores.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import com.jazaldcorp.inveragrovendedores.models.Producto;

@Dao
public interface ProductoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProductos(List<Producto> productos);

    @Query("DELETE FROM productos")
    void borrarTodos();

    @Query("SELECT * FROM productos ORDER BY nombre")
    LiveData<List<Producto>> getProductosLive();

    @Query("SELECT * FROM productos WHERE id = :productoId LIMIT 1")
    Producto getProductoById(int productoId);
}
