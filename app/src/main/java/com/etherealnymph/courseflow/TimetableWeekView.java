package com.etherealnymph.courseflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The full seven-day timetable, drawn directly on a canvas for crisp density. */
public class TimetableWeekView extends View {

    public interface OnCourseClick {
        void onClick(Store.Course course, Store.Session session, LocalDate date);
    }

    private final Palette p;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Store.Timetable table;
    private LocalDate weekMonday = Dates.mondayOf(Dates.today());
    private int weekNumber;
    private OnCourseClick listener;
    private final List<Hit> hits = new ArrayList<>();
    private int rowHeight;
    private int headerHeight;
    private int gutter;
    private float columnWidth;
    private float downX;
    private float downY;
    private boolean moved;
    private int selectedDay;
    private int selectedSlot = -1;
    private final int touchSlop;

    private static class Hit {
        final RectF rect = new RectF();
        Store.Course course;
        Store.Session session;
        LocalDate date;
        int startRow;
        int span;
    }

    public TimetableWeekView(Context context, Palette p) {
        super(context);
        this.p = p;
        rowHeight = Ui.dp(context, 50);
        headerHeight = Ui.dp(context, 62);
        gutter = Ui.dp(context, 40);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
    }

    public void setData(Store.Timetable table, LocalDate weekMonday, int weekNumber,
                        OnCourseClick listener) {
        this.table = table;
        this.weekMonday = weekMonday;
        this.weekNumber = weekNumber;
        this.listener = listener;
        selectedDay = 0;
        for (int d = 0; d < 7; d++) {
            if (weekMonday.plusDays(d).equals(Dates.today())) selectedDay = d + 1;
        }
        selectedSlot = -1;
        rebuild();
        requestLayout();
        invalidate();
    }

    private int slotCount() {
        return table == null ? 0 : Math.max(table.timeSlots.size(), 1);
    }

    private void rebuild() {
        hits.clear();
        if (table == null) return;
        LocalDate term = Dates.parseDate(table.termStart);
        if (term == null) return;
        for (int day = 1; day <= 7; day++) {
            LocalDate date = weekMonday.plusDays(day - 1L);
            for (Store.Course c : table.courses) {
                for (Store.Session s : c.sessions) {
                    if (!Store.occursOn(c, s, date, term)) continue;
                    int start = Store.slotIndex(table, s.slotId);
                    if (start < 0) continue;
                    int end = Store.sessionEndIndex(table, s);
                    if (end < start) end = start;
                    Hit hit = new Hit();
                    hit.course = c;
                    hit.session = s;
                    hit.date = date;
                    hit.startRow = start;
                    hit.span = Math.min(end - start + 1, slotCount() - start);
                    hits.add(hit);
                }
            }
        }
        setContentDescription("第 " + Math.max(weekNumber, 1) + " 周课表，共 " + hits.size() + " 节课");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) width = getResources().getDisplayMetrics().widthPixels;
        columnWidth = (width - gutter) / 7f;
        int count = slotCount();
        int height = headerHeight + count * rowHeight + Ui.dp(getContext(), 12);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (table == null) return;
        int w = getWidth();
        int count = slotCount();
        columnWidth = (w - gutter) / 7f;
        float gridTop = headerHeight;

        LocalDate today = Dates.today();
        for (int day = 1; day <= 7; day++) {
            LocalDate date = weekMonday.plusDays(day - 1L);
            float cx = gutter + (day - 1) * columnWidth + columnWidth / 2f;
            boolean isToday = date.equals(today);
            boolean selected = day == selectedDay;
            if (selected) {
                rect.set(gutter + (day - 1) * columnWidth + Ui.dp(getContext(), 2),
                        Ui.dp(getContext(), 4), gutter + day * columnWidth - Ui.dp(getContext(), 2),
                        headerHeight - Ui.dp(getContext(), 4));
                paint.setColor(p.primarySoft);
                canvas.drawRoundRect(rect, Ui.dp(getContext(), 12), Ui.dp(getContext(), 12), paint);
            }
            textPaint.setTypeface(Ui.MEDIUM);
            textPaint.setTextSize(Ui.dp(getContext(), 11));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(selected || isToday ? p.primary : p.sub);
            canvas.drawText(Dates.weekShort(day), cx, Ui.dp(getContext(), 24), textPaint);
            textPaint.setTypeface(selected ? Ui.MEDIUM : Ui.REGULAR);
            textPaint.setTextSize(Ui.dp(getContext(), 10));
            textPaint.setColor(selected || isToday ? p.primary : p.faint);
            canvas.drawText(String.valueOf(date.getDayOfMonth()), cx, Ui.dp(getContext(), 44), textPaint);
        }

        // The "周" label sits inside the gutter rather than on its edge.
        textPaint.setTypeface(Ui.MEDIUM);
        textPaint.setTextSize(Ui.dp(getContext(), 10));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(p.faint);
        canvas.drawText("周", gutter - Ui.dp(getContext(), 9), Ui.dp(getContext(), 20), textPaint);

        int todayIndex = -1;
        for (int d = 1; d <= 7; d++) {
            if (weekMonday.plusDays(d - 1L).equals(today)) todayIndex = d;
        }
        if (todayIndex > 0) {
            paint.setColor(Palette.alpha(p.primary, p.dark ? 14 : 10));
            canvas.drawRect(gutter + (todayIndex - 1) * columnWidth, gridTop,
                    gutter + todayIndex * columnWidth, gridTop + count * rowHeight, paint);
        }
        if (selectedDay >= 1 && selectedDay <= 7) {
            paint.setColor(Palette.alpha(p.primary, p.dark ? 25 : 17));
            canvas.drawRect(gutter + (selectedDay - 1) * columnWidth, gridTop,
                    gutter + selectedDay * columnWidth, gridTop + count * rowHeight, paint);
        }
        if (selectedSlot >= 0 && selectedSlot < count) {
            paint.setColor(Palette.alpha(p.accent, p.dark ? 18 : 13));
            canvas.drawRect(gutter, gridTop + selectedSlot * rowHeight, w,
                    gridTop + (selectedSlot + 1) * rowHeight, paint);
        }

        for (int i = 0; i < count; i++) {
            float top = gridTop + i * rowHeight;
            if (i % 2 == 1) {
                paint.setColor(Palette.alpha(p.surface, p.dark ? 36 : 70));
                canvas.drawRect(0, top, w, top + rowHeight, paint);
            }
            paint.setColor(p.line);
            paint.setAlpha(p.dark ? 135 : 190);
            canvas.drawRect(gutter, top, w, top + Math.max(1, Ui.dp(getContext(), 0.7f)), paint);
            paint.setAlpha(255);
            if (i < table.timeSlots.size()) {
                Store.TimeSlot slot = table.timeSlots.get(i);
                boolean active = i == selectedSlot;
                if (active) {
                    rect.set(Ui.dp(getContext(), 4), top + Ui.dp(getContext(), 4),
                            gutter - Ui.dp(getContext(), 4), top + rowHeight - Ui.dp(getContext(), 4));
                    paint.setColor(p.accentSoft);
                    canvas.drawRoundRect(rect, Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), paint);
                }
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTypeface(Ui.MEDIUM);
                textPaint.setTextSize(Ui.dp(getContext(), 8.8f));
                textPaint.setColor(active ? p.accent : p.sub);
                canvas.drawText(String.valueOf(i + 1), gutter / 2f,
                        top + Ui.dp(getContext(), 14), textPaint);
                textPaint.setTypeface(Ui.REGULAR);
                textPaint.setTextSize(Ui.dp(getContext(), 6.5f));
                textPaint.setColor(active ? p.accent : p.faint);
                String start = slot.segments.isEmpty() ? "" : slot.segments.get(0).start;
                String end = slot.segments.isEmpty() ? "" : slot.segments.get(slot.segments.size() - 1).end;
                canvas.drawText(start, gutter / 2f, top + Ui.dp(getContext(), 27), textPaint);
                textPaint.setColor(active ? p.accent : Palette.blend(p.faint, p.sub, 0.35f));
                canvas.drawText(end, gutter / 2f, top + Ui.dp(getContext(), 39), textPaint);
            }
        }
        paint.setColor(p.line);
        canvas.drawRect(gutter, gridTop, gutter + Math.max(1, Ui.dp(getContext(), 0.7f)),
                gridTop + count * rowHeight, paint);

        for (int day = 1; day <= 7; day++) {
            LocalDate date = weekMonday.plusDays(day - 1L);
            for (int i = 0; i < count; i++) {
                List<Hit> cell = new ArrayList<>();
                for (Hit h : hits) {
                    if (h.startRow == i && h.date.equals(date)) cell.add(h);
                }
                if (cell.isEmpty()) continue;
                float left = gutter + (day - 1) * columnWidth + Ui.dp(getContext(), 2);
                float right = gutter + day * columnWidth - Ui.dp(getContext(), 2);
                for (int n = 0; n < cell.size(); n++) {
                    Hit h = cell.get(n);
                    float top = gridTop + i * rowHeight + Ui.dp(getContext(), 2);
                    float bottom = gridTop + (i + h.span) * rowHeight - Ui.dp(getContext(), 2);
                    if (cell.size() > 1) {
                        float part = (right - left) / cell.size();
                        float l = left + n * part + (n == 0 ? 0 : Ui.dp(getContext(), 1));
                        float r = left + (n + 1) * part - (n == cell.size() - 1 ? 0 : Ui.dp(getContext(), 1));
                        h.rect.set(l, top, r, bottom);
                        drawCourse(canvas, h, l, top, r, bottom, cell.size());
                    } else {
                        h.rect.set(left, top, right, bottom);
                        drawCourse(canvas, h, left, top, right, bottom, 1);
                    }
                }
            }
        }

        if (hits.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Ui.REGULAR);
            textPaint.setTextSize(Ui.dp(getContext(), 12));
            textPaint.setColor(p.faint);
            canvas.drawText("这一周还没有课程", gutter + (w - gutter) / 2f,
                    gridTop + Ui.dp(getContext(), 46), textPaint);
        }
    }

    private void drawCourse(Canvas canvas, Hit h, float left, float top, float right, float bottom,
                            int stackCount) {
        int color = h.course.color;
        int pad = Ui.dp(getContext(), 3);
        int textWidth = Math.max(Ui.dp(getContext(), 16), (int) (right - left - pad * 2));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Ui.MEDIUM);
        textPaint.setTextSize(Ui.dp(getContext(), stackCount > 1 ? 7.6f : 8.2f));
        textPaint.setColor(p.ink);

        String location = Store.effectiveLocation(h.course, h.session);
        String teachers = Store.teacherLine(h.course, h.session);
        StaticLayout name = StaticLayout.Builder.obtain(h.course.name, 0, h.course.name.length(),
                        textPaint, textWidth)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0, 1f)
                .build();
        textPaint.setTypeface(Ui.REGULAR);
        textPaint.setTextSize(Ui.dp(getContext(), 7.2f));
        StaticLayout loc = location.isEmpty() ? null
                : StaticLayout.Builder.obtain(location, 0, location.length(), textPaint, textWidth)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0, 1f)
                .build();
        StaticLayout teacher = teachers.isEmpty() ? null
                : StaticLayout.Builder.obtain(teachers, 0, teachers.length(), textPaint, textWidth)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0, 1f)
                .build();

        rect.set(left, top, right, bottom);
        paint.setColor(Palette.blend(color, p.surface, p.dark ? 0.30f : 0.16f));
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 7), Ui.dp(getContext(), 7), paint);
        paint.setColor(Palette.alpha(color, p.dark ? 160 : 110));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, Ui.dp(getContext(), 0.8f)));
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 7), Ui.dp(getContext(), 7), paint);
        paint.setStyle(Paint.Style.FILL);

        rect.set(left, top + Ui.dp(getContext(), 4), left + Ui.dp(getContext(), 2.8f),
                bottom - Ui.dp(getContext(), 4));
        paint.setColor(color);
        canvas.drawRoundRect(rect, Ui.dp(getContext(), 2), Ui.dp(getContext(), 2), paint);

        textPaint.setTypeface(Ui.MEDIUM);
        textPaint.setTextSize(Ui.dp(getContext(), stackCount > 1 ? 8.8f : 9.8f));
        textPaint.setColor(p.ink);
        float y = top + pad;
        canvas.save();
        canvas.translate(left + pad, y);
        name.draw(canvas);
        canvas.restore();
        y += name.getHeight() + Ui.dp(getContext(), 3);

        if (loc != null || teacher != null) {
            paint.setColor(Palette.alpha(p.ink, 28));
            canvas.drawRect(left + pad, y, right - pad, y + Math.max(1, Ui.dp(getContext(), 0.6f)), paint);
            y += Ui.dp(getContext(), 4);
        }

        if (loc != null) {
            textPaint.setTypeface(Ui.REGULAR);
            textPaint.setTextSize(Ui.dp(getContext(), 8.4f));
            textPaint.setColor(p.sub);
            canvas.save();
            canvas.translate(left + pad, y);
            loc.draw(canvas);
            canvas.restore();
            y += loc.getHeight() + Ui.dp(getContext(), 2);
        }
        if (teacher != null && bottom - y > Ui.dp(getContext(), 8)) {
            textPaint.setTypeface(Ui.REGULAR);
            textPaint.setTextSize(Ui.dp(getContext(), 8.4f));
            textPaint.setColor(p.ink);
            canvas.save();
            canvas.translate(left + pad, y);
            teacher.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop) {
                    moved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    handleTap(event.getX(), event.getY());
                    return true;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                moved = true;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTap(float x, float y) {
        if (y < headerHeight) {
            if (x >= gutter) {
                int day = (int) ((x - gutter) / columnWidth) + 1;
                if (day < 1) day = 1;
                if (day > 7) day = 7;
                selectedDay = selectedDay == day ? 0 : day;
                invalidate();
            }
            return;
        }
        if (x < gutter) {
            int slot = (int) ((y - headerHeight) / rowHeight);
            if (slot >= 0 && slot < slotCount()) {
                selectedSlot = selectedSlot == slot ? -1 : slot;
                invalidate();
            }
            return;
        }
        for (Hit h : hits) {
            if (h.rect.contains(x, y)) {
                selectedDay = h.date.getDayOfWeek().getValue();
                selectedSlot = h.startRow;
                invalidate();
                performClick();
                if (listener != null) listener.onClick(h.course, h.session, h.date);
                return;
            }
        }
        int day = (int) ((x - gutter) / columnWidth) + 1;
        int slot = (int) ((y - headerHeight) / rowHeight);
        if (day >= 1 && day <= 7 && slot >= 0 && slot < slotCount()) {
            if (selectedDay == day && selectedSlot == slot) {
                selectedDay = 0;
                selectedSlot = -1;
            } else {
                selectedDay = day;
                selectedSlot = slot;
            }
            invalidate();
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
