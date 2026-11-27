package com.jazaldcorp.inveragrovendedores.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.adapters.ProductoCarritoAdapter;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.models.Producto;
import com.jazaldcorp.inveragrovendedores.repositories.ProductoRepository;
import com.jazaldcorp.inveragrovendedores.utils.FirmaDialogHelper;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Catálogo de productos + carrito. NO guarda ni sincroniza nada por sí sola: solo arma la
 * selección y la devuelve a RegistroVisitaFragment (vía activity result). El pedido real
 * (field.sales.order) se crea recién cuando el vendedor guarda la visita completa, para que
 * pueda armar el carrito en cualquier momento del flujo sin que se dispare un pedido suelto.
 */
public class CatalogoActivity extends AppCompatActivity {

    public static final String EXTRA_CLIENTE_ID = "CLIENTE_ID";
    public static final String EXTRA_CART_JSON = "CART_JSON";
    public static final String EXTRA_CART_TOTAL = "CART_TOTAL";
    public static final String EXTRA_SIGNATURE_B64 = "SIGNATURE_B64";

    private RecyclerView recyclerView;
    private ProductoCarritoAdapter adapter;
    private TextView tvTotalPedido, tvCatalogoVacio;
    private MaterialButton btnConfirmarPedido, btnFirmarPedido;
    private android.widget.LinearLayout llFirmaPedidoSeccion;
    private ImageView ivFirmaPedidoPreview;

    private SessionManager session;
    private int clienteId;
    private Bitmap firmaBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalogo);

        clienteId = getIntent().getIntExtra(EXTRA_CLIENTE_ID, -1);
        session = new SessionManager(this);

        findViewById(R.id.btnBackCatalogo).setOnClickListener(v -> finish());

        tvTotalPedido = findViewById(R.id.tvTotalPedido);
        tvCatalogoVacio = findViewById(R.id.tvCatalogoVacio);
        btnConfirmarPedido = findViewById(R.id.btnConfirmarPedido);
        llFirmaPedidoSeccion = findViewById(R.id.llFirmaPedidoSeccion);
        ivFirmaPedidoPreview = findViewById(R.id.ivFirmaPedidoPreview);
        btnFirmarPedido = findViewById(R.id.btnFirmarPedido);

        String colorMarca = session.getBrandingColor();
        com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarColorFondo(btnConfirmarPedido, colorMarca);
        com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarColorFondo(btnFirmarPedido, colorMarca);

        cargarConfigFirma();
        btnFirmarPedido.setOnClickListener(v -> FirmaDialogHelper.mostrar(this, bitmap -> {
            firmaBitmap = bitmap;
            ivFirmaPedidoPreview.setImageBitmap(bitmap);
            ivFirmaPedidoPreview.setVisibility(android.view.View.VISIBLE);
            btnFirmarPedido.setText("Volver a Firmar");
        }));

        recyclerView = findViewById(R.id.recyclerViewProductos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductoCarritoAdapter(this::actualizarTotal);
        recyclerView.setAdapter(adapter);

        ProductoRepository productoRepository = new ProductoRepository(this);
        productoRepository.getProductosLocales().observe(this, productos -> {
            adapter.setProductos(productos);
            tvCatalogoVacio.setVisibility(productos == null || productos.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            actualizarTotal();
        });
        productoRepository.actualizarCatalogoDesdeServidor();

        btnConfirmarPedido.setOnClickListener(v -> agregarAlPedido());
    }

    private void cargarConfigFirma() {
        llFirmaPedidoSeccion.setVisibility(session.isSignatureEnabled() ? android.view.View.VISIBLE : android.view.View.GONE);

        String token = session.getAuthToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = ApiClient.getService(this);
        apiService.getSignatureConfig("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (isFinishing() || !response.isSuccessful() || response.body() == null) return;
                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;
                if (result.get("error") != null) return;

                boolean enabled = Boolean.TRUE.equals(result.get("enabled"));
                session.saveSignatureConfig(enabled);
                llFirmaPedidoSeccion.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: seguimos con el último valor cacheado.
            }
        });
    }

    private void actualizarTotal() {
        double total = adapter.calcularTotal();
        tvTotalPedido.setText(String.format(Locale.getDefault(), "Total: S/ %.2f", total));
    }

    /**
     * Solo arma el carrito y lo devuelve al fragment de la visita (como JSON en el Intent
     * result). No toca Room ni dispara sincronización: eso ocurre recién al guardar la visita.
     */
    private void agregarAlPedido() {
        if (clienteId == -1) {
            Toast.makeText(this, "Error: cliente inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<Integer, Integer> seleccion = adapter.getCantidadesSeleccionadas();
        if (seleccion.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un producto.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray carrito = new JSONArray();
        try {
            for (Map.Entry<Integer, Integer> entry : seleccion.entrySet()) {
                Producto producto = adapter.getProductoPorId(entry.getKey());
                if (producto == null) continue;
                JSONObject linea = new JSONObject();
                linea.put("product_id", producto.id);
                linea.put("product_name", producto.nombre);
                linea.put("qty", entry.getValue());
                linea.put("price_unit", producto.precio);
                carrito.put(linea);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error armando el pedido: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_CART_JSON, carrito.toString());
        resultIntent.putExtra(EXTRA_CART_TOTAL, adapter.calcularTotal());
        if (firmaBitmap != null) {
            resultIntent.putExtra(EXTRA_SIGNATURE_B64, FirmaDialogHelper.bitmapABase64(firmaBitmap));
        }
        setResult(RESULT_OK, resultIntent);
        Toast.makeText(this, "Productos añadidos al pedido de la visita.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
