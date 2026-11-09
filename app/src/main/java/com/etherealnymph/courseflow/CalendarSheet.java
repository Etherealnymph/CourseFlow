package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.Comparator;

public final class CalendarSheet {

    public interface OnDate {
        void picked(LocalDate date);
    }

    private CalendarSheet() {}

    public static void open(MainActivity a, LocalDate initial, OnDate callback) {
        Palette p = a.palette();
        Store store = a.store();
        LocalDate selected = initial == null ? Dates.today() : initial;
        final LocalDate[] month = {selected.withDayOfMonth(1)};
        Sheet sheet = new Sheet(a, p, "选择日期", "农历 · 节假日 · 点击日期切换");
        LinearLayout body = Ui.column(a);
        sheet.add(body);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.removeAllViews();
            LocalDate first = month[0];
            LinearLayout top = Ui.row(a);
            ImageView prev = Ui.iconButton(a, R.drawable.ic_chevron_left, p.sub, p, false);
            prev.setOnClickListener(v -> {
                month[0] = first.minusMonths(1);
                render[0].run();
            });
            top.addView(prev);
            LinearLayout center = Ui.column(a);
            center.setGravity(Gravity.CENTER);
            TextView mt = Ui.text(a, first.getYear() + "年 " + first.getMonthValue() + "月",
                    18, p.ink, true);
            mt.setGravity(Gravity.CENTER);
            center.addView(mt);
            TextView lunar = Ui.text(a, LunarCalendar.monthLabel(first) + " · 农历",
                    10, p.faint, false);
            lunar.setGravity(Gravity.CENTER);
            center.addView(lunar);
            top.addView(center, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ImageView next = Ui.iconButton(a, R.drawable.ic_chevron_right, p.sub, p, false);
            next.setOnClickListener(v -> {
                month[0] = first.plusMonths(1);
                render[0].run();
            });
            top.addView(next);
            body.addView(top);

            LinearLayout week = Ui.row(a);
            String[] names = {"一", "二", "三", "四", "五", "六", "日"};
            for (String name : names) {
                TextView w = Ui.text(a, name, 10, p.faint, true);
                w.setGravity(Gravity.CENTER);
                week.addView(w, new LinearLayout.LayoutParams(0, Ui.dp(a, 24), 1));
            }
            body.addView(week);

            LinearLayout grid = Ui.column(a);
            LocalDate cursor = first.minusDays(first.getDayOfWeek().getValue() - 1L);
            for (int r = 0; r < 6; r++) {
                LinearLayout row = Ui.row(a);
                for (int c = 0; c < 7; c++) {
                    LocalDate date = cursor;
                    cursor = cursor.plusDays(1);
                    LinearLayout cell = Ui.column(a);
                    cell.setGravity(Gravity.CENTER);
                    cell.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
                    cell.setMinimumHeight(Ui.dp(a, 52));
                    boolean currentMonth = date.getMonthValue() == first.getMonthValue();
                    boolean isSelected = date.equals(selected);
                    boolean isToday = date.equals(Dates.today());
                    cell.setAlpha(currentMonth ? 1f : 0.3f);
                    if (isSelected) {
                        cell.setBackground(Ui.stroke(a, p.primarySoft, p.primary, 1.3f, 10));
                    } else if (isToday) {
                        cell.setBackground(Ui.bg(a, p.surfaceAlt, 10));
                    }
                    TextView num = Ui.text(a, String.valueOf(date.getDayOfMonth()), 13,
                            isSelected ? p.primary : (isToday ? p.primary : p.ink), isSelected || isToday);
                    num.setGravity(Gravity.CENTER);
                    cell.addView(num);
                    String holiday = store.holidayName(date);
                    boolean makeup = store.isMakeupWorkday(date);
                    String term = SolarTerms.nameOf(date);
                    String low = makeup ? "班"
                            : !holiday.isEmpty() ? holiday
                            : !term.isEmpty() ? term
                            : LunarCalendar.dayLabel(date);
                    int lowColor = makeup ? p.blue
                            : !holiday.isEmpty() ? p.accent
                            : !term.isEmpty() ? p.primary : p.faint;
                    TextView sub = Ui.text(a, low, 7.5f, lowColor,
                            makeup || !holiday.isEmpty() || !term.isEmpty());
                    sub.setGravity(Gravity.CENTER);
                    sub.setSingleLine(true);
                    sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    sub.setPadding(0, Ui.dp(a, 1), 0, 0);
                    cell.addView(sub);
                    cell.setClickable(true);
                    cell.setOnClickListener(v -> {
                        callback.picked(date);
                        sheet.dismiss();
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    lp.setMargins(Ui.dp(a, 1), 0, Ui.dp(a, 1), 0);
                    row.addView(cell, lp);
                }
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            body.addView(grid);

            LinearLayout footer = Ui.row(a);
            TextView today = Ui.textButton(a, "回到今天", p.primary, p);
            today.setOnClickListener(v -> {
                callback.picked(Dates.today());
                sheet.dismiss();
            });
            footer.addView(today, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView manage = Ui.textButton(a, "节假日设置", p.accent, p);
            manage.setOnClickListener(v -> openHolidayManager(a, () -> render[0].run()));
            footer.addView(manage);
            body.addView(footer);
        };
        render[0].run();
        sheet.show();
    }

    public static void openHolidayManager(MainActivity a, Runnable onChanged) {
        Palette p = a.palette();
        Store store = a.store();
        Sheet sheet = new Sheet(a, p, "节假日与调休", "节假日以 JSON 或手动添加，闹钟会据此判断工作日");
        LinearLayout list = Ui.column(a);
        sheet.add(list);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            if (store.data.holidays.isEmpty()) {
                TextView empty = Ui.text(a, "还没有自定义节假日。内置节日会自动显示；这里可补充调休上班日。",
                        12, p.faint, false);
                empty.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
                list.addView(empty);
            }
            store.data.holidays.sort(Comparator.comparing(h -> h.date));
            for (Store.Holiday holiday : store.data.holidays) {
                LinearLayout row = Ui.row(a);
                row.setBackground(Ui.stroke(a, p.surface, p.line, 1, 12));
                row.setPadding(Ui.dp(a, 11), Ui.dp(a, 9), Ui.dp(a, 5), Ui.dp(a, 9));
                row.setMinimumHeight(Ui.dp(a, 50));
                TextView date = Ui.text(a, holiday.date, 11, holiday.workday ? p.blue : p.accent, true);
                date.setMinWidth(Ui.dp(a, 84));
                row.addView(date);
                LinearLayout lines = Ui.column(a);
                lines.addView(Ui.text(a, holiday.name.isEmpty() ? (holiday.workday ? "调休上班" : "节假日") : holiday.name,
                        12.5f, p.ink, true));
                lines.addView(Ui.text(a, holiday.workday ? "调休上班日" : "休息日", 10, p.faint, false));
                row.addView(lines, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                ImageView del = Ui.iconButton(a, R.drawable.ic_close, p.faint, p, false);
                del.setOnClickListener(v -> {
                    store.data.holidays.remove(holiday);
                    store.save();
                    render[0].run();
                    if (onChanged != null) onChanged.run();
                });
                row.addView(del);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(a, 10);
                lp.bottomMargin = Ui.dp(a, 1);
                list.addView(row, lp);
            }
        };
        render[0].run();

        sheet.divider();
        sheet.label("手动添加");
        final LocalDate[] picked = {Dates.today()};
        TextView dateBtn = Ui.secondaryButton(a, "选择日期", p);
        dateBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        dateBtn.setOnClickListener(v -> Pickers.date(a, p, picked[0], d -> {
            picked[0] = d;
            dateBtn.setText(Dates.date(d) + " · " + Dates.weekFull(Dates.dow(d)));
        }));
        dateBtn.setText(Dates.date(picked[0]) + " · " + Dates.weekFull(Dates.dow(picked[0])));
        sheet.add(dateBtn);
        EditText name = Ui.input(a, "名称，例如：国庆节 / 调休上班", p, false);
        sheet.add(name);
        TextView type = Ui.chip(a, "节假日（休息）", p, true);
        final boolean[] workday = {false};
        type.setOnClickListener(v -> {
            workday[0] = !workday[0];
            type.setText(workday[0] ? "调休上班日" : "节假日（休息）");
            Ui.setChipSelected(type, true, p);
        });
        sheet.add(type);
        TextView add = Ui.primaryButton(a, "添加 / 更新这一天", p);
        add.setOnClickListener(v -> {
            String d = Dates.date(picked[0]);
            for (Store.Holiday h : store.data.holidays) {
                if (h.date.equals(d)) {
                    store.data.holidays.remove(h);
                    break;
                }
            }
            Store.Holiday h = new Store.Holiday();
            h.date = d;
            h.name = name.getText().toString().trim();
            h.workday = workday[0];
            store.data.holidays.add(h);
            store.save();
            name.setText("");
            render[0].run();
            if (onChanged != null) onChanged.run();
        });
        sheet.addFooter(add);
        TextView imp = Ui.secondaryButton(a, "从 JSON 导入节假日", p);
        imp.setOnClickListener(v -> {
            sheet.dismiss();
            a.importHolidays();
        });
        sheet.addFooter(imp);
        TextView fmt = Ui.textButton(a, "查看导入格式示例", p.sub, p);
        fmt.setOnClickListener(v -> showHolidayFormat(a));
        sheet.addFooter(fmt);
        sheet.show();
    }

    public static void showHolidayFormat(MainActivity a) {
        Palette p = a.palette();
        Sheet sheet = new Sheet(a, p, "节假日 JSON 格式", "保存为 .json 后在节假日设置中导入");
        TextView code = new TextView(a);
        code.setText("{\n"
                + "  \"holidays\": [\n"
                + "    { \"date\": \"2026-10-01\", \"name\": \"国庆节\", \"type\": \"holiday\" },\n"
                + "    { \"date\": \"2026-10-10\", \"name\": \"调休上班\", \"type\": \"workday\" }\n"
                + "  ]\n"
                + "}");
        code.setTextSize(11);
        code.setTypeface(android.graphics.Typeface.MONOSPACE);
        code.setTextColor(p.sub);
        code.setBackground(Ui.bg(a, p.surfaceAlt, 12));
        code.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        code.setTextIsSelectable(true);
        sheet.add(code);
        TextView hint = Ui.text(a, "type=holiday 表示休息日；type=workday 表示调休上班日。未列出的日期按周一至周五为工作日、周六周日为休息日。",
                12, p.sub, false);
        hint.setPadding(0, Ui.dp(a, 10), 0, 0);
        sheet.add(hint);
        TextView close = Ui.primaryButton(a, "知道了", p);
        close.setOnClickListener(v -> sheet.dismiss());
        sheet.addFooter(close);
        sheet.show();
    }
}
