package com.jazaldcorp.inveragrovendedores.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.jazaldcorp.inveragrovendedores.dao.AsistenciaDao;
import com.jazaldcorp.inveragrovendedores.dao.ClienteDao;
import com.jazaldcorp.inveragrovendedores.dao.PedidoDao;
import com.jazaldcorp.inveragrovendedores.dao.ProductoDao;
import com.jazaldcorp.inveragrovendedores.dao.RutaParadaDao;
import com.jazaldcorp.inveragrovendedores.dao.UbicacionPingDao;
import com.jazaldcorp.inveragrovendedores.dao.UsuarioDao;
import com.jazaldcorp.inveragrovendedores.dao.VisitaDao;
import com.jazaldcorp.inveragrovendedores.models.Asistencia;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import com.jazaldcorp.inveragrovendedores.models.Pedido;
import com.jazaldcorp.inveragrovendedores.models.PedidoLinea;
import com.jazaldcorp.inveragrovendedores.models.Producto;
import com.jazaldcorp.inveragrovendedores.models.RutaParada;
import com.jazaldcorp.inveragrovendedores.models.UbicacionPing;
import com.jazaldcorp.inveragrovendedores.models.Usuario;
import com.jazaldcorp.inveragrovendedores.models.Visita;
import com.jazaldcorp.inveragrovendedores.models.VisitaMultimedia;

// Aquí declaras todas las tablas que vivirán en la app
@Database(entities = {Usuario.class, Cliente.class, Asistencia.class, Visita.class, VisitaMultimedia.class,
        Producto.class, Pedido.class, PedidoLinea.class, RutaParada.class, UbicacionPing.class}, version = 13, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // Vinculamos los DAOs
    public abstract UsuarioDao usuarioDao();
    public abstract ClienteDao clienteDao();
    public abstract AsistenciaDao asistenciaDao();
    public abstract VisitaDao visitaDao();
    public abstract ProductoDao productoDao();
    public abstract PedidoDao pedidoDao();
    public abstract RutaParadaDao rutaParadaDao();
    public abstract UbicacionPingDao ubicacionPingDao();

    // Patrón Singleton para evitar fugas de memoria
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "agro_ventas_local_db")
                            .fallbackToDestructiveMigration() // Si cambias tablas, borra y recrea localmente
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}