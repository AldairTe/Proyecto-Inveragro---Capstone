package com.jazaldcorp.inveragrovendedores.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.models.Visita;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VisitaAdapter extends RecyclerView.Adapter<VisitaAdapter.VisitaViewHolder> {

    private List<Visita> listaVisitas = new ArrayList<>();

    public void setVisitas(List<Visita> visitas) {
        this.listaVisitas = visitas;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VisitaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_visita, parent, false);
        return new VisitaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VisitaViewHolder holder, int position) {
        Visita visita = listaVisitas.get(position);

        // 1. Manejo de la fecha: Prioriza el timestamp local en milisegundos, o usa date_visit de Odoo
        if (visita.fecha_hora > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.getDefault());
            String fechaLegible = sdf.format(new Date(visita.fecha_hora));
            holder.tvFecha.setText(fechaLegible);
        } else if (visita.date_visit != null && !visita.date_visit.isEmpty()) {
            holder.tvFecha.setText(visita.date_visit);
        } else {
            holder.tvFecha.setText("Fecha no disponible");
        }

        // 2. Usamos el campo "notes" actualizado de tu modelo de Odoo
        if (visita.notes != null && !visita.notes.trim().isEmpty()) {
            holder.tvObservacion.setText(visita.notes);
        } else {
            holder.tvObservacion.setText("Sin notas adicionales.");
        }
    }

    @Override
    public int getItemCount() {
        return listaVisitas.size();
    }

    static class VisitaViewHolder extends RecyclerView.ViewHolder {
        TextView tvFecha, tvObservacion;

        public VisitaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tvFechaVisita);
            tvObservacion = itemView.findViewById(R.id.tvObservacionVisita);
        }
    }
}