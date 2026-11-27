package com.jazaldcorp.inveragrovendedores.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.models.Cliente;
import java.util.ArrayList;
import java.util.List;

public class ClienteAdapter extends RecyclerView.Adapter<ClienteAdapter.ClienteViewHolder> {

    public interface OnClienteClickListener {
        void onClienteClick(Cliente cliente);
    }

    private List<Cliente> listaClientes = new ArrayList<>();
    private List<Integer> idsVisitados = new ArrayList<>();
    private OnClienteClickListener clickListener;

    public void setOnClienteClickListener(OnClienteClickListener listener) {
        this.clickListener = listener;
    }

    public void setClientes(List<Cliente> clientes, List<Integer> idsVisitados) {
        // Blindaje contra nulos provenientes del repositorio o base de datos
        this.listaClientes = clientes != null ? clientes : new ArrayList<>();
        this.idsVisitados = idsVisitados != null ? idsVisitados : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cliente, parent, false);
        return new ClienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClienteViewHolder holder, int position) {
        if (listaClientes == null || position >= listaClientes.size()) {
            return;
        }

        Cliente cliente = listaClientes.get(position);
        if (cliente == null) return;

        holder.tvNombre.setText(cliente.nombre_contacto != null ? cliente.nombre_contacto : "");
        holder.tvFundo.setText(cliente.nombre_fundo != null ? cliente.nombre_fundo : "");

        // --- LÓGICA DE ESTADO SEGURA ---
        boolean esVisitado = idsVisitados != null && idsVisitados.contains(cliente.id);

        if (esVisitado) {
            holder.tvEstado.setText("VISITADO");
            holder.tvEstado.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        } else {
            holder.tvEstado.setText("PENDIENTE");
            holder.tvEstado.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        }

        // Lógica para sacar iniciales
        String iniciales = "";
        if (cliente.nombre_contacto != null && !cliente.nombre_contacto.isEmpty()) {
            String[] partes = cliente.nombre_contacto.trim().split("\\s+");
            if (partes.length > 0 && !partes[0].isEmpty()) {
                iniciales += partes[0].substring(0, 1).toUpperCase();
            }
            if (partes.length > 1 && !partes[1].isEmpty()) {
                iniciales += partes[1].substring(0, 1).toUpperCase();
            }
        }
        holder.tvIniciales.setText(iniciales);

        // Lógica de distancia
        if (cliente.distanciaCalculada != null) {
            holder.tvDistancia.setText(cliente.distanciaCalculada);
        } else {
            holder.tvDistancia.setText("Calculando...");
        }

        holder.tvTelefono.setText(cliente.telefono != null && !cliente.telefono.isEmpty() ? "📞 " + cliente.telefono : "");

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClienteClick(cliente);
        });
    }

    public void calcularDistancia(int position, double latActual, double lngActual) {
        if (listaClientes == null || position < 0 || position >= listaClientes.size()) {
            return;
        }

        Cliente cliente = listaClientes.get(position);
        if (cliente == null) return;

        float[] results = new float[1];
        android.location.Location.distanceBetween(
                latActual, lngActual,
                cliente.latitud, cliente.longitud,
                results
        );

        float distanciaKm = results[0] / 1000;
        String textoDistancia = String.format("%.1f km", distanciaKm);

        cliente.distanciaCalculada = textoDistancia;
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        // Blindaje contra null pointer exception
        return listaClientes != null ? listaClientes.size() : 0;
    }

    static class ClienteViewHolder extends RecyclerView.ViewHolder {
        TextView tvIniciales, tvNombre, tvFundo, tvEstado, tvDistancia, tvTelefono;

        public ClienteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIniciales = itemView.findViewById(R.id.tvItemIniciales);
            tvNombre = itemView.findViewById(R.id.tvItemNombre);
            tvFundo = itemView.findViewById(R.id.tvItemFundo);
            tvEstado = itemView.findViewById(R.id.tvItemEstado);
            tvDistancia = itemView.findViewById(R.id.tvItemDistancia);
            tvTelefono = itemView.findViewById(R.id.tvItemTelefono);
        }
    }
}