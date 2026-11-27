package com.jazaldcorp.inveragrovendedores.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.activities.LoginActivity;
import com.jazaldcorp.inveragrovendedores.database.AppDatabase;
import com.jazaldcorp.inveragrovendedores.models.Usuario;
import com.jazaldcorp.inveragrovendedores.repositories.SincronizacionRepository;
import com.jazaldcorp.inveragrovendedores.utils.BrandingHelper;
import com.jazaldcorp.inveragrovendedores.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private MaterialButton btnCerrarSesion;
    private TextView tvPerfilNombre, tvPerfilRol, tvUltimaSincronizacion, tvSeccionCuenta;
    private View btnSincronizacion;
    private AppDatabase db;
    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = AppDatabase.getInstance(requireContext());
        session = new SessionManager(requireContext());

        tvPerfilNombre = view.findViewById(R.id.tvPerfilNombre);
        tvPerfilRol = view.findViewById(R.id.tvPerfilRol);
        tvUltimaSincronizacion = view.findViewById(R.id.tvUltimaSincronizacion);
        tvSeccionCuenta = view.findViewById(R.id.tvSeccionCuenta);
        btnSincronizacion = view.findViewById(R.id.btnSincronizacion);
        btnCerrarSesion = view.findViewById(R.id.btnCerrarSesion);
        View btnMiRuta = view.findViewById(R.id.btnMiRuta);

        BrandingHelper.aplicarColorTexto(tvSeccionCuenta, session.getBrandingColor());

        cargarDatosUsuario();
        actualizarTextoUltimaSincronizacion();

        btnSincronizacion.setOnClickListener(v -> ejecutarSincronizacionManual());
        btnCerrarSesion.setOnClickListener(v -> ejecutarCerrarSesion());
        btnMiRuta.setOnClickListener(v -> requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new MiRutaFragment())
                .addToBackStack(null)
                .commit());
    }

    private void cargarDatosUsuario() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Usuario usuario = db.usuarioDao().getUsuarioLogueado();
            if (usuario == null || !isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                tvPerfilNombre.setText(usuario.nombre_completo != null ? usuario.nombre_completo : "Vendedor");

                StringBuilder rol = new StringBuilder("Vendedor");
                if (usuario.zonaVenta != null && !usuario.zonaVenta.isEmpty()) {
                    rol.append(" · ").append(usuario.zonaVenta);
                }
                if (usuario.dni != null && !usuario.dni.isEmpty()) {
                    rol.append(" · ").append(usuario.dni);
                }
                tvPerfilRol.setText(rol.toString());
            });
        });
    }

    private void ejecutarSincronizacionManual() {
        btnSincronizacion.setEnabled(false);
        tvUltimaSincronizacion.setText("Sincronizando...");

        SincronizacionRepository syncRepo = new SincronizacionRepository(requireContext());
        syncRepo.ejecutarSincronizacionCompleta(() -> {
            if (!isAdded()) return;
            btnSincronizacion.setEnabled(true);
            actualizarTextoUltimaSincronizacion();
            Toast.makeText(requireContext(), "Sincronización completada.", Toast.LENGTH_SHORT).show();
        });
    }

    private void actualizarTextoUltimaSincronizacion() {
        long millis = session.getUltimaSincronizacionMillis();
        if (millis <= 0) {
            tvUltimaSincronizacion.setText("Aún no se ha sincronizado en este dispositivo.");
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("es", "ES"));
        tvUltimaSincronizacion.setText("Última sincronización: " + sdf.format(new Date(millis)));
    }

    private void ejecutarCerrarSesion() {
        SessionManager session = new SessionManager(requireContext());
        session.logout();

        requireContext().stopService(new Intent(requireContext(), com.jazaldcorp.inveragrovendedores.services.LocationTrackingService.class));

        Toast.makeText(getContext(), "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
