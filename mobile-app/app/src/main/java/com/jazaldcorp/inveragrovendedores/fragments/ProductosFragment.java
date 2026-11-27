package com.jazaldcorp.inveragrovendedores.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.adapters.ProductoCarritoAdapter;
import com.jazaldcorp.inveragrovendedores.repositories.ProductoRepository;

/**
 * Catálogo de productos de solo consulta, accesible desde el bottom nav sin necesidad de
 * estar registrando una visita o un pedido. Reutiliza ProductoCarritoAdapter en modo lectura.
 */
public class ProductosFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProductoCarritoAdapter adapter;
    private TextView tvVacio;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_productos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewProductosCatalogo);
        tvVacio = view.findViewById(R.id.tvProductosVacio);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProductoCarritoAdapter(null, true);
        recyclerView.setAdapter(adapter);

        ProductoRepository productoRepository = new ProductoRepository(requireContext());
        productoRepository.getProductosLocales().observe(getViewLifecycleOwner(), productos -> {
            adapter.setProductos(productos);
            tvVacio.setVisibility(productos == null || productos.isEmpty() ? View.VISIBLE : View.GONE);
        });
        productoRepository.actualizarCatalogoDesdeServidor();
    }
}
