package com.jazaldcorp.inveragrovendedores.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.models.Producto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lista de productos del catálogo con un stepper de cantidad por fila.
 * El "carrito" es el propio adapter: cantidades > 0 se consideran seleccionadas.
 */
public class ProductoCarritoAdapter extends RecyclerView.Adapter<ProductoCarritoAdapter.ProductoViewHolder> {

    public interface OnCarritoChangeListener {
        void onCarritoChanged();
    }

    private List<Producto> productos = new ArrayList<>();
    private final Map<Integer, Integer> cantidades = new HashMap<>();
    private final OnCarritoChangeListener listener;
    private final boolean soloLectura;

    public ProductoCarritoAdapter(OnCarritoChangeListener listener) {
        this(listener, false);
    }

    /** soloLectura=true oculta el stepper de cantidad (para el catálogo de solo consulta). */
    public ProductoCarritoAdapter(OnCarritoChangeListener listener, boolean soloLectura) {
        this.listener = listener;
        this.soloLectura = soloLectura;
    }

    public void setProductos(List<Producto> productos) {
        this.productos = productos != null ? productos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public Map<Integer, Integer> getCantidadesSeleccionadas() {
        Map<Integer, Integer> seleccionados = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : cantidades.entrySet()) {
            if (entry.getValue() > 0) seleccionados.put(entry.getKey(), entry.getValue());
        }
        return seleccionados;
    }

    public double calcularTotal() {
        double total = 0;
        for (Producto p : productos) {
            Integer cant = cantidades.get(p.id);
            if (cant != null && cant > 0) total += cant * p.precio;
        }
        return total;
    }

    public Producto getProductoPorId(int id) {
        for (Producto p : productos) {
            if (p.id == id) return p;
        }
        return null;
    }

    @NonNull
    @Override
    public ProductoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_producto, parent, false);
        return new ProductoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoViewHolder holder, int position) {
        Producto producto = productos.get(position);
        holder.tvNombre.setText(producto.nombre);

        String detalle = (producto.codigo != null && !producto.codigo.isEmpty() ? producto.codigo + " · " : "")
                + (producto.unidadMedida != null ? producto.unidadMedida : "");
        holder.tvDetalle.setText(detalle);
        holder.tvPrecio.setText(String.format(Locale.getDefault(), "S/ %.2f", producto.precio));

        boolean controlaStock = producto.stockDisponible != null;
        boolean sinStock = controlaStock && producto.stockDisponible <= 0;

        if (controlaStock) {
            holder.tvStock.setVisibility(View.VISIBLE);
            holder.tvStock.setText(sinStock
                    ? "Sin stock"
                    : String.format(Locale.getDefault(), "Stock: %.0f", producto.stockDisponible));
            holder.tvStock.setTextColor(sinStock ? 0xFFD32F2F : 0xFF757575);
        } else {
            holder.tvStock.setVisibility(View.GONE);
        }

        if (soloLectura) {
            holder.tvCantidad.setVisibility(View.GONE);
            holder.btnSumar.setVisibility(View.GONE);
            holder.btnRestar.setVisibility(View.GONE);
            return;
        }

        int cantidadActual = cantidades.containsKey(producto.id) ? cantidades.get(producto.id) : 0;
        holder.tvCantidad.setText(String.valueOf(cantidadActual));
        holder.btnSumar.setEnabled(!sinStock);

        holder.btnSumar.setOnClickListener(v -> {
            int actual = cantidades.containsKey(producto.id) ? cantidades.get(producto.id) : 0;
            if (controlaStock && actual + 1 > producto.stockDisponible) {
                Toast.makeText(holder.itemView.getContext(),
                        "No hay más stock disponible de " + producto.nombre, Toast.LENGTH_SHORT).show();
                return;
            }
            int nueva = actual + 1;
            cantidades.put(producto.id, nueva);
            holder.tvCantidad.setText(String.valueOf(nueva));
            if (listener != null) listener.onCarritoChanged();
        });

        holder.btnRestar.setOnClickListener(v -> {
            int actual = cantidades.containsKey(producto.id) ? cantidades.get(producto.id) : 0;
            int nueva = Math.max(0, actual - 1);
            cantidades.put(producto.id, nueva);
            holder.tvCantidad.setText(String.valueOf(nueva));
            if (listener != null) listener.onCarritoChanged();
        });
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    static class ProductoViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvDetalle, tvPrecio, tvCantidad, tvStock;
        MaterialButton btnSumar, btnRestar;

        public ProductoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvProductoNombre);
            tvDetalle = itemView.findViewById(R.id.tvProductoDetalle);
            tvPrecio = itemView.findViewById(R.id.tvProductoPrecio);
            tvCantidad = itemView.findViewById(R.id.tvCantidad);
            tvStock = itemView.findViewById(R.id.tvStock);
            btnSumar = itemView.findViewById(R.id.btnSumar);
            btnRestar = itemView.findViewById(R.id.btnRestar);
        }
    }
}
