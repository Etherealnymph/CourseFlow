package com.etherealnymph.courseflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Locale;

/** A small wheel picker: drag up/down to change the number. */
public class WheelPicker extends View {

    private final Palette p;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int min;
    private int max;
    private int value;
    private float lastY;
    private float accumulator;
    private float offset;
    private android.animation.ValueAnimator snap;
    private final int touchSlop;
    private OnChange onChange;

    public interface OnChange {
        void changed(int value);
    }

    public WheelPicker(Context context, Palette p, int min, int max, int value) {
        super(context);
        this.p = p;
        this.min = min;
        this.max = max;
        this.value = Math.max(min, Math.min(max, value));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
        setContentDescription("滚动选择数值");
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setOnChange(OnChange onChange) {
        this.onChange = onChange;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = Math.max(min, Math.min(max, value));
        this.offset = 0;
        this.accumulator = 0;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w == 0) w = Ui.dp(getContext(), 120);
        setMeasuredDimension(w, Ui.dp(getContext(), 188));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float rowH = getHeight() / 5f;
        float centerY = getHeight() / 2f;
        paint.setColor(p.primarySoft);
        rect.set(Ui.dp(getContext(), 6), centerY - rowH / 2f,
                getWidth() - Ui.dp(getContext(), 6), centerY + rowH / 2f);
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 12), Ui.dp(getContext(), 12), paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = -3; i <= 3; i++) {
            int n = value + i;
            if (n < min || n > max) continue;
            float position = centerY + i * rowH + offset;
            float distance = Math.abs(position - centerY) / rowH;
            float emphasis = Math.max(0f, 1f - distance / 1.6f);
            textPaint.setTypeface(emphasis > 0.55f ? Ui.MEDIUM : Ui.REGULAR);
            textPaint.setTextSize(Ui.dp(getContext(), 15 + emphasis * 10));
            int color = Palette.blend(p.faint, p.primary, emphasis);
            textPaint.setColor(color);
            textPaint.setAlpha((int) (110 + emphasis * 145));
            float y = position - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(String.format(Locale.CHINA, "%02d", n), getWidth() / 2f, y, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastY = event.getY();
                accumulator = offset;
                if (snap != null) snap.cancel();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - lastY;
                lastY = event.getY();
                accumulator += dy;
                float rowH = getHeight() / 5f;
                while (accumulator >= rowH) {
                    if (value > min) {
                        value--;
                        accumulator -= rowH;
                    } else {
                        accumulator = 0;
                        break;
                    }
                }
                while (accumulator <= -rowH) {
                    if (value < max) {
                        value++;
                        accumulator += rowH;
                    } else {
                        accumulator = 0;
                        break;
                    }
                }
                offset = accumulator;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (onChange != null) onChange.changed(value);
                snapBack();
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void snapBack() {
        if (snap != null) snap.cancel();
        snap = android.animation.ValueAnimator.ofFloat(offset, 0f);
        snap.setDuration(110);
        snap.addUpdateListener(animation -> {
            offset = (float) animation.getAnimatedValue();
            invalidate();
        });
        snap.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (snap != null) snap.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
