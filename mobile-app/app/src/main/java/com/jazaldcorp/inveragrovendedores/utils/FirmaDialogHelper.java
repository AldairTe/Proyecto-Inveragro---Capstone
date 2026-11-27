package com.jazaldcorp.inveragrovendedores.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.jazaldcorp.inveragrovendedores.R;
import com.jazaldcorp.inveragrovendedores.views.SignatureView;

import java.io.ByteArrayOutputStream;

/** Diálogo reutilizable para capturar la firma del cliente (visitas y pedidos). */
public class FirmaDialogHelper {

    public interface OnFirmaCapturada {
        void onFirmaCapturada(Bitmap bitmap);
    }

    public static void mostrar(Context context, OnFirmaCapturada callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_firma, null);
        SignatureView signatureView = view.findViewById(R.id.signatureView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.btnLimpiarFirma).setOnClickListener(v -> signatureView.limpiar());

        view.findViewById(R.id.btnGuardarFirma).setOnClickListener(v -> {
            if (!signatureView.tieneFirma()) {
                Toast.makeText(context, "Aún no hay ninguna firma en el recuadro.", Toast.LENGTH_SHORT).show();
                return;
            }
            callback.onFirmaCapturada(signatureView.obtenerBitmap());
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Convierte la firma capturada a Base64 (PNG), listo para enviar en el JSON-RPC a Odoo. */
    public static String bitmapABase64(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }
}
