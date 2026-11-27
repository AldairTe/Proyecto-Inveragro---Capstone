package com.jazaldcorp.inveragrovendedores.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.api.ApiClient;
import com.jazaldcorp.inveragrovendedores.api.ApiService;
import com.jazaldcorp.inveragrovendedores.activities.CatalogoActivity;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Pedido;
import com.jazaldcorp.inveragrovendedores.models.PedidoLinea;
import com.jazaldcorp.inveragrovendedores.models.Usuario;
import com.jazaldcorp.inveragrovendedores.models.Visita;
import com.jazaldcorp.inveragrovendedores.models.VisitaMultimedia;
import com.jazaldcorp.inveragrovendedores.repositories.SincronizacionRepository;
import com.jazaldcorp.inveragrovendedores.utils.FirmaDialogHelper;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;
import com.jazaldcorp.inveragrovendedores.utils.WhatsAppShareHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistroVisitaFragment extends Fragment {

    private int clienteId;
    private AppDatabase db;

    private TextView tvFechaActual, tvEstadoMultimedia, tvEstadoProductos;
    private TextInputEditText etNotas;
    private ImageView btnBack;
    private MaterialButton btnAnadirProducto, btnAdjuntarMultimedia, btnFinalizarGuardar;

    // Carrito de productos armado en CatalogoActivity, pendiente de guardar junto con la visita.
    // productId -> {"nombre":..., "cantidad":..., "precio":...} (se acumula si se abre el catálogo varias veces).
    private final Map<Integer, Object[]> carritoPedido = new LinkedHashMap<>();
    private String firmaPedidoBase64;

    private final ActivityResultLauncher<Intent> catalogoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                String cartJson = data.getStringExtra(CatalogoActivity.EXTRA_CART_JSON);
                String signatureB64 = data.getStringExtra(CatalogoActivity.EXTRA_SIGNATURE_B64);
                if (signatureB64 != null) firmaPedidoBase64 = signatureB64;

                if (cartJson == null) return;
                try {
                    JSONArray array = new JSONArray(cartJson);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject linea = array.getJSONObject(i);
                        int productId = linea.getInt("product_id");
                        String nombre = linea.getString("product_name");
                        double cantidad = linea.getDouble("qty");
                        double precio = linea.getDouble("price_unit");

                        Object[] existente = carritoPedido.get(productId);
                        double cantidadFinal = existente != null ? ((double) existente[1] + cantidad) : cantidad;
                        carritoPedido.put(productId, new Object[]{nombre, cantidadFinal, precio});
                    }
                    actualizarEstadoProductos();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error leyendo el carrito: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
    );

    private List<Uri> listaUrisMultimedia = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;
    private String notasPendientes;
    private long timestampPendiente;

    private SessionManager session;
    private LinearLayout llChecklistSeccion, llChecklistItems, llFirmaSeccion;
    private ImageView ivFirmaPreview;
    private MaterialButton btnFirmar;
    private final Map<CheckBox, Integer> checklistCheckboxes = new LinkedHashMap<>();
    private Bitmap firmaBitmap;

    private final ActivityResultLauncher<String> requestLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    obtenerUbicacionYGuardar();
                } else {
                    Toast.makeText(getContext(), "Se requiere permiso de ubicación para registrar la visita.", Toast.LENGTH_LONG).show();
                    btnFinalizarGuardar.setEnabled(true);
                }
            });

    // Lanzador nativo para seleccionar múltiples imágenes/videos de la galería
    // Cambiamos el tipo MIME a "*/*" para que la galería permita seleccionar tanto imágenes como videos
    private final ActivityResultLauncher<String> seleccionarMultimediaLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    listaUrisMultimedia = uris;
                    tvEstadoMultimedia.setText("✅ " + listaUrisMultimedia.size() + " archivo(s) seleccionado(s).");
                } else {
                    tvEstadoMultimedia.setText("Aún no se han adjuntado archivos.");
                }
            }
    );



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registro_visita, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            clienteId = getArguments().getInt("CLIENTE_ID", -1);
        }

        db = AppDatabase.getInstance(requireContext());
        session = new SessionManager(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        enlazarVistas(view);

        String colorMarca = session.getBrandingColor();
        com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarColorFondo(btnAdjuntarMultimedia, colorMarca);
        com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarColorFondo(btnAnadirProducto, colorMarca);
        com.jazaldcorp.inveragrovendedores.utils.BrandingHelper.aplicarColorFondo(btnFirmar, colorMarca);

        long timestampActual = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy • HH:mm", new Locale("es", "ES"));
        tvFechaActual.setText(sdf.format(new Date(timestampActual)));

        cargarChecklist();
        cargarConfigFirma();

        btnFirmar.setOnClickListener(v -> FirmaDialogHelper.mostrar(requireContext(), bitmap -> {
            firmaBitmap = bitmap;
            ivFirmaPreview.setImageBitmap(bitmap);
            ivFirmaPreview.setVisibility(View.VISIBLE);
            btnFirmar.setText("Volver a Firmar");
        }));

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        btnAnadirProducto.setOnClickListener(v -> {
            if (clienteId == -1) {
                Toast.makeText(requireContext(), "Error: ID de cliente inválido.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(requireContext(), CatalogoActivity.class);
            intent.putExtra(CatalogoActivity.EXTRA_CLIENTE_ID, clienteId);
            catalogoLauncher.launch(intent);
        });

        // Y cuando lanzas el intent en el botón, usa "*/*" o especifica ambos:
        btnAdjuntarMultimedia.setOnClickListener(v -> {
            seleccionarMultimediaLauncher.launch("*/*");
        });

        btnFinalizarGuardar.setOnClickListener(v -> {
            String notasTexto = etNotas.getText().toString().trim();

            if (notasTexto.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor, ingresa notas de la reunión.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (clienteId == -1) {
                Toast.makeText(requireContext(), "Error: ID de cliente inválido.", Toast.LENGTH_SHORT).show();
                return;
            }

            notasPendientes = notasTexto;
            timestampPendiente = timestampActual;
            btnFinalizarGuardar.setEnabled(false);
            verificarPermisosGpsYGuardar();
        });
    }

    private void verificarPermisosGpsYGuardar() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacionYGuardar();
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacionYGuardar() {
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(requireActivity(), location -> {
                    double lat = location != null ? location.getLatitude() : 0.0;
                    double lon = location != null ? location.getLongitude() : 0.0;
                    if (location == null) {
                        Toast.makeText(requireContext(), "No se pudo obtener el GPS, la visita se guardará sin coordenadas.", Toast.LENGTH_LONG).show();
                    }
                    guardarVisitaLocal(notasPendientes, timestampPendiente, lat, lon);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Error del sensor GPS: " + e.getMessage() + ". Se guardará sin coordenadas.", Toast.LENGTH_LONG).show();
                    guardarVisitaLocal(notasPendientes, timestampPendiente, 0.0, 0.0);
                });
    }

    private void enlazarVistas(View view) {
        tvFechaActual = view.findViewById(R.id.tvFechaActual);
        tvEstadoMultimedia = view.findViewById(R.id.tvEstadoMultimedia);
        etNotas = view.findViewById(R.id.etNotasVisita);
        btnBack = view.findViewById(R.id.btnBackVisita);
        btnAnadirProducto = view.findViewById(R.id.btnAnadirProducto);
        btnAdjuntarMultimedia = view.findViewById(R.id.btnAdjuntarMultimedia);
        btnFinalizarGuardar = view.findViewById(R.id.btnFinalizarGuardar);
        llChecklistSeccion = view.findViewById(R.id.llChecklistSeccion);
        llChecklistItems = view.findViewById(R.id.llChecklistItems);
        llFirmaSeccion = view.findViewById(R.id.llFirmaSeccion);
        ivFirmaPreview = view.findViewById(R.id.ivFirmaPreview);
        btnFirmar = view.findViewById(R.id.btnFirmar);
        tvEstadoProductos = view.findViewById(R.id.tvEstadoProductos);
    }

    private void actualizarEstadoProductos() {
        if (carritoPedido.isEmpty()) {
            tvEstadoProductos.setText("Aún no se han agregado productos.");
            return;
        }
        double total = 0;
        for (Object[] linea : carritoPedido.values()) {
            total += (double) linea[1] * (double) linea[2];
        }
        tvEstadoProductos.setText(String.format(Locale.getDefault(),
                "✅ %d producto(s) agregado(s) · Total: S/ %.2f", carritoPedido.size(), total));
    }

    private void cargarChecklist() {
        String token = session.getAuthToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = ApiClient.getService(requireContext());
        apiService.getChecklistItems("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) return;

                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Object itemsObj = ((Map<String, Object>) resultObj).get("items");
                if (!(itemsObj instanceof List)) return;

                List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
                if (items.isEmpty()) return;

                llChecklistItems.removeAllViews();
                checklistCheckboxes.clear();

                for (Map<String, Object> item : items) {
                    Object idObj = item.get("id");
                    if (!(idObj instanceof Number)) continue;
                    int itemId = ((Number) idObj).intValue();
                    String nombre = item.get("name") != null ? item.get("name").toString() : "";

                    CheckBox checkBox = new CheckBox(requireContext());
                    checkBox.setText(nombre);
                    llChecklistItems.addView(checkBox);
                    checklistCheckboxes.put(checkBox, itemId);
                }

                llChecklistSeccion.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: la visita se guarda igual, solo sin checklist esta vez.
            }
        });
    }

    private void cargarConfigFirma() {
        // Mientras responde el servidor, mostramos el último estado cacheado para no
        // depender de la conexión en cada visita.
        llFirmaSeccion.setVisibility(session.isSignatureEnabled() ? View.VISIBLE : View.GONE);

        String token = session.getAuthToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = ApiClient.getService(requireContext());
        apiService.getSignatureConfig("Bearer " + token, new Object()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) return;

                Object resultObj = response.body().get("result");
                if (!(resultObj instanceof Map)) return;
                Map<String, Object> result = (Map<String, Object>) resultObj;
                if (result.get("error") != null) return;

                boolean enabled = Boolean.TRUE.equals(result.get("enabled"));
                session.saveSignatureConfig(enabled);
                llFirmaSeccion.setVisibility(enabled ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Sin conexión: seguimos con el último valor cacheado ya aplicado arriba.
            }
        });
    }

    private String construirChecklistJson() {
        JSONArray array = new JSONArray();
        try {
            for (Map.Entry<CheckBox, Integer> entry : checklistCheckboxes.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("item_id", entry.getValue());
                obj.put("checked", entry.getKey().isChecked());
                array.put(obj);
            }
        } catch (Exception ignored) {
        }
        return array.toString();
    }

    private void guardarVisitaLocal(String notas, long timestamp, double latitud, double longitud) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Usuario vendedor = db.usuarioDao().getUsuarioLogueado();
            if (vendedor == null) {
                requireActivity().runOnUiThread(() -> btnFinalizarGuardar.setEnabled(true));
                return;
            }

            String uuidVisita = UUID.randomUUID().toString();

            Visita nuevaVisita = new Visita();
            nuevaVisita.external_uuid = uuidVisita;
            nuevaVisita.customer_id = clienteId;
            nuevaVisita.employee_id = vendedor.id;

            SimpleDateFormat sdfOdoo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            nuevaVisita.date_visit = sdfOdoo.format(new Date(timestamp));

            nuevaVisita.latitude = latitud;
            nuevaVisita.longitude = longitud;
            nuevaVisita.notes = notas;
            nuevaVisita.state = "completed";
            nuevaVisita.fecha_hora = timestamp;
            nuevaVisita.sincronizado = false;
            nuevaVisita.checklistJson = construirChecklistJson();
            nuevaVisita.signatureBase64 = firmaBitmap != null ? FirmaDialogHelper.bitmapABase64(firmaBitmap) : null;

            // 1. Guardar la visita
            db.visitaDao().insertVisita(nuevaVisita);

            // 2. Guardar las Uris multimedia asociadas si el usuario seleccionó alguna
            // (misma ubicación que la visita: es la mejor aproximación disponible ya que
            // los archivos se eligen de la galería y no se capturan en vivo con la cámara)
            if (listaUrisMultimedia != null && !listaUrisMultimedia.isEmpty()) {
                List<VisitaMultimedia> listaMultimediaDb = new ArrayList<>();
                for (Uri uri : listaUrisMultimedia) {
                    listaMultimediaDb.add(new VisitaMultimedia(uuidVisita, uri.toString(), latitud, longitud));
                }
                db.visitaDao().insertMultimediaList(listaMultimediaDb);
            }

            double montoPedido = 0;

            // 3. Si se armó un carrito de productos durante la visita, se crea el pedido
            // recién ahora, junto con la visita, en vez de por separado.
            if (!carritoPedido.isEmpty()) {
                String uuidPedido = UUID.randomUUID().toString();

                Pedido pedido = new Pedido();
                pedido.external_uuid = uuidPedido;
                pedido.customer_id = clienteId;
                pedido.employee_id = vendedor.id;
                pedido.date_order = sdfOdoo.format(new Date(timestamp));
                pedido.fecha_hora = timestamp;
                pedido.sincronizado = false;
                pedido.signatureBase64 = firmaPedidoBase64;

                List<PedidoLinea> lineasDb = new ArrayList<>();
                double total = 0;
                for (Map.Entry<Integer, Object[]> entry : carritoPedido.entrySet()) {
                    String nombre = (String) entry.getValue()[0];
                    double cantidad = (double) entry.getValue()[1];
                    double precio = (double) entry.getValue()[2];
                    total += cantidad * precio;
                    lineasDb.add(new PedidoLinea(uuidPedido, entry.getKey(), nombre, cantidad, precio));
                }
                pedido.montoTotal = total;
                montoPedido = total;

                db.pedidoDao().insertPedido(pedido);
                db.pedidoDao().insertLineas(lineasDb);
            }

            SincronizacionRepository syncRepo = new SincronizacionRepository(requireContext());
            syncRepo.ejecutarSincronizacionCompleta();

            com.jazaldcorp.inveragrovendedores.models.Cliente cliente = db.clienteDao().getClienteById(clienteId);
            String nombreCliente = cliente != null ? cliente.nombre_fundo : "Cliente";
            String resumen = construirResumenParaCompartir(nombreCliente, notas, carritoPedido.size(), montoPedido);

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "✅ Visita y multimedia guardadas correctamente.", Toast.LENGTH_LONG).show();
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Visita guardada")
                        .setMessage("¿Quieres compartir un resumen de esta visita por WhatsApp?")
                        .setPositiveButton("Compartir", (dialog, which) ->
                                WhatsAppShareHelper.compartirTexto(requireContext(), resumen))
                        .setNegativeButton("No, gracias", null)
                        .setOnDismissListener(d -> requireActivity().getSupportFragmentManager().popBackStack())
                        .show();
            });
        });
    }

    private String construirResumenParaCompartir(String nombreCliente, String notas, int cantidadProductos, double montoPedido) {
        SimpleDateFormat sdfLegible = new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("es", "ES"));
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Resumen de visita\n");
        sb.append("Cliente: ").append(nombreCliente).append("\n");
        sb.append("Fecha: ").append(sdfLegible.format(new Date())).append("\n");
        if (notas != null && !notas.isEmpty()) {
            sb.append("Notas: ").append(notas).append("\n");
        }
        if (cantidadProductos > 0) {
            sb.append(String.format(Locale.getDefault(),
                    "Pedido: %d producto(s) · Total: S/ %.2f\n", cantidadProductos, montoPedido));
        }
        return sb.toString();
    }
}