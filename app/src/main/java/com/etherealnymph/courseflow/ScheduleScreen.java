package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;

public class ScheduleScreen implements MainActivity.Screen {

    private final MainActivity a;
    private final Store store;
    private final Palette p;
    private final Store.Timetable table;
    private final LocalDate termStart;
    private final LocalDate currentMonday;
    private LocalDate weekMonday;
    private int weekNo;

    public ScheduleScreen(MainActivity a) {
        this.a = a;
        this.store = a.store();
        this.p = a.palette();
        this.table = store.data.timetable;
        this.termStart = Dates.parseDate(table.termStart);
        this.currentMonday = Dates.mondayOf(Dates.today());
        this.weekMonday = currentMonday.plusWeeks(a.scheduleWeekOffset);
        this.weekNo = termStart == null ? 0 : Dates.weekOf(termStart, weekMonday.plusDays(3));
    }

    @Override
    public View build() {
        LinearLayout root = Ui.column(a);
        root.setClipChildren(false);

        LinearLayout header = Ui.column(a);
        header.setPadding(Ui.dp(a, 18), Ui.dp(a, 3), Ui.dp(a, 18), 0);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout brandRow = Ui.row(a);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.addView(Ui.eyebrow(a, "SCHEDULE · 课表", p), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView theme = Ui.iconButton(a, a.isDark() ? R.drawable.ic_sun : R.drawable.ic_moon,
                p.sub, p, false);
        theme.setContentDescription("切换日间 / 夜间");
        theme.setOnClickListener(v -> a.toggleTheme());
        brandRow.addView(theme);
        ImageView more = Ui.iconButton(a, R.drawable.ic_more, p.sub, p, false);
        more.setContentDescription("更多课表操作");
        more.setOnClickListener(v -> showMore());
        brandRow.addView(more);
        brandRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(brandRow);

        LinearLayout titleRow = Ui.row(a);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(a, 5);
        header.addView(titleRow, titleLp);
        LinearLayout titleCol = Ui.column(a);
        String weekTitle = weekNo <= 0 ? "学期前" : "第 " + weekNo + " 周";
        TextView big = Ui.title(a, weekTitle, p);
        big.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 19);
        titleCol.addView(big);
        TextView sub = Ui.text(a, table.name, 10.5f, p.faint, false);
        sub.setPadding(0, Ui.dp(a, 3), 0, 0);
        titleCol.addView(sub);
        titleRow.addView(titleCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (a.scheduleWeekOffset != 0) {
            TextView back = Ui.chip(a, "回到本周", p, true);
            back.setOnClickListener(v -> {
                a.scheduleWeekOffset = 0;
                a.refresh();
            });
            titleRow.addView(back);
        } else if (weekNo > 0) {
            TextView thisWeek = Ui.pill(a, "本周", p.primary, p);
            titleRow.addView(thisWeek);
        }

        LinearLayout nav = Ui.row(a);
        nav.setBackground(Ui.stroke(a, p.surface, p.line, 1, 14));
        nav.setPadding(Ui.dp(a, 3), Ui.dp(a, 2), Ui.dp(a, 3), Ui.dp(a, 2));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navLp.topMargin = Ui.dp(a, 9);
        header.addView(nav, navLp);
        ImageView prev = Ui.iconButton(a, R.drawable.ic_chevron_left, p.sub, p, false);
        prev.setContentDescription("上一周");
        prev.setOnClickListener(v -> {
            a.scheduleWeekOffset--;
            a.refresh();
        });
        nav.addView(prev);
        String navText = (weekNo > 0 ? "第 " + weekNo + " 周  ·  " : "")
                + Dates.monthDayDot(weekMonday) + "–" + Dates.monthDayDot(weekMonday.plusDays(6)) + " ▾";
        TextView center = Ui.text(a, navText, 11.5f, p.ink, true);
        center.setGravity(Gravity.CENTER);
        center.setBackground(Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 10),
                Palette.alpha(p.primary, 22)));
        center.setContentDescription("切换周次");
        center.setOnClickListener(v -> showWeekPicker());
        nav.addView(center, new LinearLayout.LayoutParams(0,
                Ui.dp(a, 38), 1));
        ImageView next = Ui.iconButton(a, R.drawable.ic_chevron_right, p.sub, p, false);
        next.setContentDescription("下一周");
        next.setOnClickListener(v -> {
            a.scheduleWeekOffset++;
            a.refresh();
        });
        nav.addView(next);

        FrameLayout gridWrap = new FrameLayout(a);
        gridWrap.setBackground(Ui.stroke(a, p.surface, p.line, 1, 22));
        gridWrap.setClipToOutline(true);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        gridLp.setMargins(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        root.addView(gridWrap, gridLp);

        if (table.timeSlots.isEmpty()) {
            LinearLayout empty = Ui.emptyState(a, R.drawable.ic_clock, "还没有时间段",
                    "先定义一节课的开始与结束时间，再安排课程。", p);
            empty.setGravity(Gravity.CENTER);
            TextView action = Ui.primaryButton(a, "设置时间段", p);
            action.setOnClickListener(v -> TimetableSheets.slotManager(a));
            empty.addView(action, new LinearLayout.LayoutParams(
                    Ui.dp(a, 176), ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.gravity = Gravity.CENTER;
            gridWrap.addView(empty, elp);
        } else {
            ScrollView scroll = new ScrollView(a);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 68));
            TimetableWeekView week = new TimetableWeekView(a, p);
            week.setData(table, weekMonday, weekNo, (course, session, date) ->
                    TimetableSheets.courseDetail(a, course, date));
            scroll.addView(week, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            gridWrap.addView(scroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        FrameLayout fab = new FrameLayout(a);
        fab.setBackground(Ui.pressable(a, p.primary, Ui.dp(a, 28), Palette.alpha(p.onPrimary, 40)));
        fab.setElevation(Ui.dp(a, 8));
        ImageView plus = Ui.icon(a, R.drawable.ic_plus, 24, p.onPrimary);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24));
        plp.gravity = Gravity.CENTER;
        fab.addView(plus, plp);
        fab.setContentDescription("添加课程");
        fab.setOnClickListener(v -> TimetableSheets.editCourse(a, null));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(a, 54), Ui.dp(a, 54));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.setMargins(0, 0, Ui.dp(a, 16), Ui.dp(a, 16));
        gridWrap.addView(fab, flp);

        return root;
    }

    private void showWeekPicker() {
        LocalDate term = Dates.parseDate(table.termStart);
        int currentWeek = term == null ? 1 : Dates.weekOf(term, Dates.today());
        int maxWeek = 20;
        if (term != null) {
            for (Store.Course c : table.courses) {
                for (Store.Session s : c.sessions) {
                    maxWeek = Math.max(maxWeek, WeekSpec.maxWeek(s.effectiveWeeks()));
                }
            }
        }
        maxWeek = Math.max(maxWeek, Math.max(weekNo, currentWeek) + 3);
        maxWeek = Math.min(40, maxWeek);

        Sheet sheet = new Sheet(a, p, "切换周次", "第一周 " + table.termStart
                + " · 当前第 " + Math.max(1, currentWeek) + " 周");
        LinearLayout row = null;
        for (int w = 1; w <= maxWeek; w++) {
            if ((w - 1) % 3 == 0) {
                row = Ui.row(a);
                sheet.add(row, weekRowLp(w == 1));
            }
            LocalDate monday = Dates.weekStart(term == null ? Dates.mondayOf(Dates.today()) : term, w);
            boolean selected = w == weekNo;
            boolean thisWeek = w == currentWeek;
            LinearLayout cell = Ui.column(a);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(Ui.dp(a, 3), Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 8));
            cell.setBackground(selected
                    ? Ui.stroke(a, p.primarySoft, p.primary, 1.4f, 12)
                    : Ui.pressable(a, p.surfaceAlt, Ui.dp(a, 12), Palette.alpha(p.ink, 16)));
            TextView title = Ui.text(a, "第 " + w + " 周", 12, selected ? p.primary : p.ink, true);
            title.setGravity(Gravity.CENTER);
            cell.addView(title);
            TextView dates = Ui.text(a, Dates.monthDayDot(monday) + " – "
                    + Dates.monthDayDot(monday.plusDays(6)), 9, p.faint, false);
            dates.setGravity(Gravity.CENTER);
            dates.setPadding(0, Ui.dp(a, 3), 0, 0);
            cell.addView(dates);
            if (thisWeek) {
                TextView badge = Ui.text(a, "本周", 8, p.primary, true);
                badge.setGravity(Gravity.CENTER);
                cell.addView(badge);
            }
            final int targetWeek = w;
            cell.setClickable(true);
            cell.setOnClickListener(v -> {
                a.scheduleWeekOffset = targetWeek - currentWeek;
                sheet.dismiss();
                a.refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(a, 2), 0, Ui.dp(a, 2), 0);
            row.addView(cell, lp);
        }
        if (maxWeek % 3 != 0) {
            int missing = 3 - (maxWeek % 3);
            for (int i = 0; i < missing; i++) {
                row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
            }
        }
        if (a.scheduleWeekOffset != 0) {
            TextView back = Ui.secondaryButton(a, "回到本周", p);
            back.setOnClickListener(v -> {
                a.scheduleWeekOffset = 0;
                sheet.dismiss();
                a.refresh();
            });
            sheet.addFooter(back);
        }
        sheet.show();
    }

    private LinearLayout.LayoutParams weekRowLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, first ? 8 : 5);
        return lp;
    }

    private void showMore() {
        Sheet sheet = new Sheet(a, p, "课程表", table.name + "  ·  第一周 " + table.termStart);
        addAction(sheet, R.drawable.ic_plus, "添加课程", "课程名称、地点、多位教师与上课时间", () -> {
            sheet.dismiss();
            TimetableSheets.editCourse(a, null);
        });
        addAction(sheet, R.drawable.ic_clock, "管理时间段", "支持一个时间段包含多个分段", () -> {
            sheet.dismiss();
            TimetableSheets.slotManager(a);
        });
        addAction(sheet, R.drawable.ic_calendar, "学期与第一周", "设置开学日期与学期名称", () -> {
            sheet.dismiss();
            TimetableSheets.editTerm(a);
        });
        sheet.divider();
        addAction(sheet, R.drawable.ic_download, "导入 JSON 课程表", "从文件替换当前课程表", () -> {
            sheet.dismiss();
            a.importTimetable();
        });
        addAction(sheet, R.drawable.ic_upload, "导出 JSON 课程表", "生成可编辑、可备份的 JSON 文件", () -> {
            sheet.dismiss();
            a.exportTimetable();
        });
        addAction(sheet, R.drawable.ic_info, "JSON 格式", "查看当前课程表的完整结构", () -> {
            sheet.dismiss();
            SettingsScreen.showTimetableFormat(a);
        });
        addAction(sheet, R.drawable.ic_spark, "载入 2026–2027 第一学期",
                "内置本学期课表 · 11 节作息", () -> {
                    sheet.dismiss();
                    a.loadSemesterTimetable();
                });
        sheet.show();
    }

    static void addAction(Sheet sheet, int iconRes, String title, String subtitle, Runnable action) {
        android.content.Context context = sheet.dialog().getContext();
        // Dialog wraps the Activity in a ContextThemeWrapper; unwrap it instead
        // of casting the wrapper directly (that used to crash the ⋯ menu).
        while (context instanceof android.content.ContextWrapper) {
            android.content.Context base =
                    ((android.content.ContextWrapper) context).getBaseContext();
            if (base == null || base == context) break;
            context = base;
        }
        if (!(context instanceof MainActivity)) return;
        MainActivity a = (MainActivity) context;
        Palette p = sheet.palette();
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8));
        row.setBackground(Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 13), Palette.alpha(p.ink, 16)));
        FrameLayout iconWrap = new FrameLayout(a);
        iconWrap.setBackground(Ui.bg(a, p.surfaceAlt, 12));
        ImageView icon = Ui.icon(a, iconRes, 18, p.primary);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18));
        ilp.gravity = Gravity.CENTER;
        iconWrap.addView(icon, ilp);
        row.addView(iconWrap, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        LinearLayout lines = Ui.column(a);
        lines.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
        lines.addView(Ui.text(a, title, 14, p.ink, true));
        if (subtitle != null) {
            TextView s = Ui.text(a, subtitle, 11, p.faint, false);
            s.setPadding(0, Ui.dp(a, 3), 0, 0);
            lines.addView(s);
        }
        row.addView(lines, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.icon(a, R.drawable.ic_chevron_right, 17, p.faint));
        row.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 2);
        row.setOnClickListener(v -> action.run());
        sheet.add(row, lp);
    }

    @Override
    public boolean onBack() {
        if (a.scheduleWeekOffset != 0) {
            a.scheduleWeekOffset = 0;
            a.refresh();
            return true;
        }
        return false;
    }
}
