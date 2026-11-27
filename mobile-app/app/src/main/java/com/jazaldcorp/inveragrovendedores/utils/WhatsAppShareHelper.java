package com.jazaldcorp.inveragrovendedores.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

/** Comparte un resumen de texto por WhatsApp (o cualquier app si WhatsApp no está instalado). */
public class WhatsAppShareHelper {

    public static void compartirTexto(Context context, String texto) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, texto);

        if (esAppInstalada(context, "com.whatsapp")) {
            intent.setPackage("com.whatsapp");
            try {
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {
                // Si falla igual, caemos al selector genérico de abajo.
            }
        }

        intent.setPackage(null);
        try {
            context.startActivity(Intent.createChooser(intent, "Compartir resumen"));
        } catch (Exception e) {
            Toast.makeText(context, "No hay ninguna app disponible para compartir.", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean esAppInstalada(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
