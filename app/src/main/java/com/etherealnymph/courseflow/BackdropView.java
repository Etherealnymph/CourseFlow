package com.etherealnymph.courseflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** A quiet, hand-painted backdrop: two soft light pools and a faint horizon. */
public class BackdropView extends View {

    private final Palette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float w;
    private float h;

    public BackdropView(Context context, Palette palette) {
        super(context);
        this.palette = palette;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        w = width;
        h = height;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(palette.bg);

        paint.setShader(new RadialGradient(w * 0.92f, h * 0.06f, w * 0.85f,
                new int[]{Palette.alpha(palette.primary, palette.dark ? 46 : 28), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(-w * 0.15f, h * 0.78f, w * 0.9f,
                new int[]{Palette.alpha(palette.accent, palette.dark ? 26 : 16), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                Color.TRANSPARENT, Palette.alpha(palette.bgAlt, palette.dark ? 150 : 90),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.72f, w, h, paint);

        paint.setShader(null);
        paint.setColor(Palette.alpha(palette.ink, palette.dark ? 12 : 8));
        float step = Ui.dp(getContext(), 26);
        float radius = Ui.dp(getContext(), 0.7f);
        for (float x = w * 0.06f; x < w * 0.94f; x += step) {
            for (float y = h * 0.18f; y < h * 0.42f; y += step) {
                canvas.drawCircle(x, y, radius, paint);
            }
        }
    }
}
