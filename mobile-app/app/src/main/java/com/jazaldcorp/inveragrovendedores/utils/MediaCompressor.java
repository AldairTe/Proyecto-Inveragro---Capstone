package com.jazaldcorp.inveragrovendedores.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprime fotos y videos antes de subirlos, para no saturar el servidor del cliente
 * (los Odoo de cada empresa suelen correr en instancias modestas, sin CDN de por medio).
 */
public class MediaCompressor {

    private static final String TAG = "MediaCompressor";

    // Fotos: se reescalan al lado más largo y se recomprimen a JPEG.
    private static final int MAX_IMAGE_DIMENSION = 1600;
    private static final int JPEG_QUALITY = 75;

    // Videos: se limita el bitrate objetivo (Transformer respeta la resolución de origen
    // salvo que se agregue un Effect de escalado; el ahorro principal viene del bitrate/codec).
    private static final int VIDEO_TRANSFORM_TIMEOUT_MIN = 5;

    private final Context context;

    public MediaCompressor(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Comprime una imagen JPEG. Si algo falla, devuelve el archivo original sin tocar. */
    public File comprimirImagen(File original) {
        try {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(original.getAbsolutePath(), boundsOptions);

            int sampleSize = calcularInSampleSize(boundsOptions, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(original.getAbsolutePath(), decodeOptions);
            if (bitmap == null) return original;

            bitmap = redimensionarSiExcede(bitmap, MAX_IMAGE_DIMENSION);
            bitmap = corregirRotacionExif(original, bitmap);

            File comprimido = File.createTempFile("comp_", ".jpg", context.getCacheDir());
            try (FileOutputStream out = new FileOutputStream(comprimido)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            }
            bitmap.recycle();

            if (comprimido.length() > 0 && comprimido.length() < original.length()) {
                Log.d(TAG, "Imagen comprimida: " + original.length() + " -> " + comprimido.length() + " bytes");
                original.delete();
                return comprimido;
            }
            comprimido.delete();
            return original;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo comprimir la imagen, se sube el original: " + e.getMessage());
            return original;
        }
    }

    /**
     * Transcodifica un video a un bitrate/formato más liviano usando Media3 Transformer.
     * Debe llamarse desde un hilo de background: internamente espera (con timeout) a que
     * el Transformer -que corre en el hilo principal- termine.
     */
    public File comprimirVideo(File original) {
        File salida;
        try {
            salida = File.createTempFile("comp_", ".mp4", context.getCacheDir());
            salida.delete(); // Transformer requiere que el archivo destino no exista
        } catch (Exception e) {
            Log.e(TAG, "No se pudo crear archivo temporal de video: " + e.getMessage());
            return original;
        }

        CountDownLatch latch = new CountDownLatch(1);
        boolean[] exito = {false};

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(original));
                EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();

                Transformer transformer = new Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(new Transformer.Listener() {
                            @Override
                            public void onCompleted(Composition composition, ExportResult exportResult) {
                                exito[0] = true;
                                latch.countDown();
                            }

                            @Override
                            public void onError(Composition composition, ExportResult exportResult, ExportException exception) {
                                Log.e(TAG, "Error comprimiendo video, se sube el original: " + exception.getMessage());
                                latch.countDown();
                            }
                        })
                        .build();

                transformer.start(editedMediaItem, salida.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Excepción iniciando Transformer: " + e.getMessage());
                latch.countDown();
            }
        });

        try {
            latch.await(VIDEO_TRANSFORM_TIMEOUT_MIN, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (exito[0] && salida.exists() && salida.length() > 0) {
            Log.d(TAG, "Video comprimido: " + original.length() + " -> " + salida.length() + " bytes");
            original.delete();
            return salida;
        }

        salida.delete();
        return original;
    }

    private int calcularInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap redimensionarSiExcede(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxDimension && height <= maxDimension) return bitmap;

        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int nuevoAncho = Math.round(width * ratio);
        int nuevoAlto = Math.round(height * ratio);
        Bitmap escalado = Bitmap.createScaledBitmap(bitmap, nuevoAncho, nuevoAlto, true);
        if (escalado != bitmap) bitmap.recycle();
        return escalado;
    }

    private Bitmap corregirRotacionExif(File original, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(original.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotacion;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotacion = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotacion = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotacion = 270; break;
                default: return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(rotacion);
            Bitmap rotado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotado != bitmap) bitmap.recycle();
            return rotado;
        } catch (Exception e) {
            return bitmap;
        }
    }
}
