package com.jazaldcorp.inveragrovendedores.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Canvas simple para capturar la firma del cliente con el dedo. */
public class SignatureView extends View {

    private Path path = new Path();
    private final Paint paint = new Paint();
    private Bitmap bitmap;
    private Canvas canvas;
    private boolean tieneTrazo = false;

    public SignatureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(6f);
        setBackgroundColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bitmap = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas viewCanvas) {
        super.onDraw(viewCanvas);
        if (bitmap != null) viewCanvas.drawBitmap(bitmap, 0, 0, null);
        viewCanvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                tieneTrazo = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
                canvas.drawPath(path, paint);
                path.reset();
                break;
            default:
                return false;
        }
        invalidate();
        return true;
    }

    public boolean tieneFirma() {
        return tieneTrazo;
    }

    public void limpiar() {
        path.reset();
        tieneTrazo = false;
        if (canvas != null) canvas.drawColor(Color.WHITE);
        invalidate();
    }

    public Bitmap obtenerBitmap() {
        return bitmap;
    }
}
