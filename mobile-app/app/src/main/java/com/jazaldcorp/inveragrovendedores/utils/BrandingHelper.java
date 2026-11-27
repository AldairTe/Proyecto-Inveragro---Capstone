package com.jazaldcorp.inveragrovendedores.utils;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Aplica el logo/color cacheados en SessionManager a los widgets de una pantalla. */
public class BrandingHelper {

    public static void aplicarLogo(ImageView imageView, String logoBase64) {
        if (imageView == null || logoBase64 == null || logoBase64.isEmpty()) return;
        try {
            byte[] bytes = Base64.decode(logoBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) imageView.setImageBitmap(bitmap);
        } catch (Exception ignored) {
            // Logo inválido o corrupto: se mantiene el logo genérico de la app.
        }
    }

    public static Integer parseColor(String colorHex) {
        if (colorHex == null || colorHex.isEmpty()) return null;
        try {
            return Color.parseColor(colorHex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void aplicarColorFondo(View view, String colorHex) {
        Integer color = parseColor(colorHex);
        if (view == null || color == null) return;
        view.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    public static void aplicarColorTexto(TextView textView, String colorHex) {
        Integer color = parseColor(colorHex);
        if (textView == null || color == null) return;
        textView.setTextColor(color);
    }

    /** Tiñe el ítem seleccionado del bottom navigation con el color de marca. */
    public static void aplicarBottomNav(BottomNavigationView nav, String colorHex) {
        Integer color = parseColor(colorHex);
        if (nav == null || color == null) return;

        int[][] estados = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{android.R.attr.state_pressed},
                new int[]{},
        };
        int[] colores = new int[]{color, color, Color.parseColor("#B8B8B8")};
        ColorStateList lista = new ColorStateList(estados, colores);

        nav.setItemIconTintList(lista);
        nav.setItemTextColor(lista);
    }
}
