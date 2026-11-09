package com.etherealnymph.courseflow;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;

public final class Pickers {

    public interface OnDate {
        void picked(LocalDate date);
    }

    public interface OnTime {
        void picked(int hour, int minute);
    }

    public interface OnNumber {
        void picked(int value);
    }

    private Pickers() {}

    public static void date(Activity activity, Palette p, LocalDate initial, OnDate callback) {
        int theme = p.dark
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
        DatePickerDialog dialog = new DatePickerDialog(activity, theme,
                (view, year, month, day) -> callback.picked(LocalDate.of(year, month + 1, day)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.setTitle("选择日期");
        dialog.getWindow().setDimAmount(0.45f);
        dialog.show();
    }

    /** Two vertically draggable wheels; no native clock dialog. */
    public static void time(Activity activity, Palette p, LocalTime initial, OnTime callback) {
        Sheet sheet = new Sheet(activity, p, "选择时间", null);
        LinearLayout row = Ui.row(activity);
        row.setGravity(Gravity.CENTER);
        WheelPicker hour = new WheelPicker(activity, p, 0, 23, initial.getHour());
        WheelPicker minute = new WheelPicker(activity, p, 0, 59, initial.getMinute());
        row.addView(hour, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView colon = Ui.text(activity, ":", 26, p.faint, true);
        colon.setPadding(Ui.dp(activity, 4), 0, Ui.dp(activity, 4), 0);
        row.addView(colon);
        row.addView(minute, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sheet.add(row);

        LinearLayout units = Ui.row(activity);
        units.setGravity(Gravity.CENTER);
        TextView h = Ui.text(activity, "时", 11, p.faint, false);
        h.setGravity(Gravity.CENTER);
        TextView m = Ui.text(activity, "分", 11, p.faint, false);
        m.setGravity(Gravity.CENTER);
        units.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        units.addView(Ui.hspacer(activity, 28));
        units.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sheet.add(units);

        TextView confirm = Ui.primaryButton(activity, "确定", p);
        confirm.setOnClickListener(v -> {
            callback.picked(hour.getValue(), minute.getValue());
            sheet.dismiss();
        });
        sheet.addFooter(confirm);
        sheet.show();
    }

    public static void number(Activity activity, Palette p, String title, String unit,
                              int min, int max, int initial, OnNumber callback) {
        Sheet sheet = new Sheet(activity, p, title, null);
        WheelPicker wheel = new WheelPicker(activity, p, min, max, initial);
        LinearLayout row = Ui.row(activity);
        row.setGravity(Gravity.CENTER);
        row.addView(wheel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView suffix = Ui.text(activity, unit, 13, p.sub, false);
        suffix.setGravity(Gravity.CENTER);
        row.addView(suffix, new LinearLayout.LayoutParams(Ui.dp(activity, 70),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.add(row);
        TextView confirm = Ui.primaryButton(activity, "确定", p);
        confirm.setOnClickListener(v -> {
            callback.picked(wheel.getValue());
            sheet.dismiss();
        });
        sheet.addFooter(confirm);
        sheet.show();
    }
}
