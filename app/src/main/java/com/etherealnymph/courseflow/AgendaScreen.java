package com.etherealnymph.courseflow;

import android.graphics.Paint;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AgendaScreen implements MainActivity.Screen {

    private final MainActivity a;
    private final Store store;
    private final Palette p;
    private final Store.Timetable table;
    private final LocalDate termStart;
    private final boolean weekMode;
    private LocalDate selectedDate;
    private LocalDate weekMonday;

    private static class Occ {
        Store.Course course;
        Store.Session session;
        Store.TimeSlot startSlot;
        Store.TimeSlot endSlot;
    }

    public AgendaScreen(MainActivity a) {
        this.a = a;
        this.store = a.store();
        this.p = a.palette();
        this.table = store.data.timetable;
        this.termStart = Dates.parseDate(table.termStart);
        this.weekMode = a.agendaWeekMode;
        LocalDate d = Dates.parseDate(a.agendaSelectedDate);
        this.selectedDate = d == null ? Dates.today() : d;
        this.weekMonday = weekMode
                ? Dates.mondayOf(Dates.today()).plusWeeks(a.agendaWeekOffset)
                : Dates.mondayOf(selectedDate);
    }

    @Override
    public View build() {
        LinearLayout root = Ui.column(a);
        LinearLayout header = Ui.column(a);
        header.setPadding(Ui.dp(a, 18), Ui.dp(a, 3), Ui.dp(a, 18), 0);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = Ui.row(a);
        LinearLayout titles = Ui.column(a);
        titles.addView(Ui.eyebrow(a, "AGENDA · 日程", p));
        LocalDate ref = weekMode ? weekMonday : selectedDate;
        int weekNo = termStart == null ? 0 : Dates.weekOf(termStart, ref);
        String weekLabel = weekNo > 0 ? "第 " + weekNo + " 周" : "学期前";
        TextView meta = Ui.text(a, weekLabel + "  ·  "
                + Dates.weekRange(weekMode ? weekMonday : Dates.mondayOf(selectedDate)),
                10.5f, p.sub, false);
        meta.setPadding(0, Ui.dp(a, 2), 0, 0);
        titles.addView(meta);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView add = Ui.iconButton(a, R.drawable.ic_plus, p.primary, p, true);
        add.setContentDescription("添加待办");
        add.setOnClickListener(v -> a.openNewTodo());
        top.addView(add);
        header.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(buildSegment(), segmentLp());
        root.addView(buildContent(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private LinearLayout.LayoutParams segmentLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 7);
        lp.bottomMargin = Ui.dp(a, 2);
        return lp;
    }

    private LinearLayout buildSegment() {
        LinearLayout seg = Ui.row(a);
        seg.setBackground(Ui.bg(a, p.surfaceAlt, 14));
        seg.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        seg.addView(segmentButton("日视图", !weekMode), new LinearLayout.LayoutParams(0,
                Ui.dp(a, 36), 1));
        seg.addView(segmentButton("周视图", weekMode), new LinearLayout.LayoutParams(0,
                Ui.dp(a, 36), 1));
        return seg;
    }

    private TextView segmentButton(String label, boolean selected) {
        TextView tv = Ui.text(a, label, 13, selected ? p.ink : p.faint, selected);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(selected ? Ui.stroke(a, p.surface, p.line, 1, 11)
                : Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 11), Palette.alpha(p.ink, 15)));
        tv.setClickable(true);
        tv.setOnClickListener(v -> {
            a.agendaWeekMode = label.equals("周视图");
            a.refresh();
        });
        return tv;
    }

    private View buildContent() {
        ScrollView scroll = Ui.screenScroll(a, p);
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 18), Ui.dp(a, 4), Ui.dp(a, 18), Ui.dp(a, 24));
        scroll.addView(body);
        if (!AlarmScheduler.canPostNotifications(a)) {
            body.addView(notificationBanner(), bannerLp());
        }
        if (weekMode) buildWeek(body);
        else buildDay(body);
        return scroll;
    }

    private LinearLayout.LayoutParams bannerLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 12);
        return lp;
    }

    private View notificationBanner() {
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.bg(a, p.accentSoft, 14));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        card.addView(Ui.icon(a, R.drawable.ic_bell, 18, p.accent));
        TextView txt = Ui.text(a, "待办提醒还未开启，到点不会弹窗。", 11.5f, p.sub, false);
        txt.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
        card.addView(txt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView open = Ui.textButton(a, "开启", p.accent, p);
        open.setOnClickListener(v -> a.maybeAskNotifications(true));
        card.addView(open);
        return card;
    }

    // ---------------------------------------------------------------- day

    private void buildDay(LinearLayout body) {
        body.addView(buildWeekStrip());

        LinearLayout dayHead = Ui.row(a);
        dayHead.setPadding(0, Ui.dp(a, 18), 0, 0);
        dayHead.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean isToday = selectedDate.equals(Dates.today());
        TextView dTitle = Ui.text(a, isToday ? "今天 · " + Dates.weekFull(Dates.dow(selectedDate))
                : Dates.monthDay(selectedDate) + " · " + Dates.weekFull(Dates.dow(selectedDate)),
                21, p.ink, true);
        dayHead.addView(dTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!isToday) {
            TextView today = Ui.textButton(a, "今天", p.primary, p);
            today.setOnClickListener(v -> {
                a.agendaSelectedDate = Dates.date(Dates.today());
                a.refresh();
            });
            dayHead.addView(today);
        }
        body.addView(dayHead);
        int selectedWeek = termStart == null ? 1 : Dates.weekOf(termStart, selectedDate);
        TextView dayMeta = Ui.text(a, selectedDate.getYear() + "年 · "
                + (selectedWeek > 0 ? "第 " + selectedWeek + " 周" : "学期前"), 11.5f, p.faint, false);
        dayMeta.setPadding(0, Ui.dp(a, 4), 0, 0);
        body.addView(dayMeta);

        List<Occ> occurrences = occurrences(selectedDate);
        List<Store.Todo> todos = store.todosOn(selectedDate);
        List<Store.Todo> overdue = new ArrayList<>();
        if (selectedDate.isAfter(Dates.today()) || selectedDate.equals(Dates.today())) {
            for (Store.Todo t : store.data.todos) {
                if (!t.done && t.dueAt > 0 && Dates.dateOf(t.dueAt).isBefore(selectedDate)
                        && Dates.dateOf(t.dueAt).isBefore(Dates.today())) {
                    overdue.add(t);
                }
            }
        }

        LinearLayout courses = Ui.column(a);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionLp.topMargin = Ui.dp(a, 22);
        body.addView(sectionHeader("课程", occurrences.size() + " 节"), sectionLp);
        body.addView(courses, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (occurrences.isEmpty()) {
            body.addView(emptyRow("今天没有课", "留一点空白，也很好。"));
        } else {
            for (int i = 0; i < occurrences.size(); i++) {
                courses.addView(courseCard(occurrences.get(i), i == 0), cardLp());
            }
        }

        LinearLayout tasksHead = Ui.row(a);
        TextView label = Ui.section(a, "待办", p);
        tasksHead.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = Ui.textButton(a, "＋ 添加", p.primary, p);
        add.setOnClickListener(v -> a.openNewTodo());
        tasksHead.addView(add);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(a, 22);
        body.addView(tasksHead, tl);

        if (overdue.isEmpty() && todos.isEmpty()) {
            body.addView(emptyRow("今天没有待办", "把想做的事写下来，时间到了会提醒你。"));
        } else {
            if (!overdue.isEmpty()) {
                TextView over = Ui.text(a, "逾期未完成 · " + overdue.size(), 11, p.danger, true);
                over.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 2));
                body.addView(over);
                for (Store.Todo t : overdue) body.addView(todoRow(t, selectedDate), cardLp());
            }
            if (!todos.isEmpty()) {
                for (Store.Todo t : todos) body.addView(todoRow(t, selectedDate), cardLp());
            }
        }
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        return lp;
    }

    private View buildWeekStrip() {
        LocalDate monday = Dates.mondayOf(selectedDate);
        LinearLayout wrap = Ui.column(a);
        LinearLayout nav = Ui.row(a);
        ImageView prev = Ui.iconButton(a, R.drawable.ic_chevron_left, p.sub, p, false);
        prev.setContentDescription("上一周");
        prev.setOnClickListener(v -> {
            a.agendaSelectedDate = Dates.date(selectedDate.minusWeeks(1));
            a.refresh();
        });
        nav.addView(prev);
        TextView range = Ui.text(a, Dates.weekRange(monday) + "  ▾", 11, p.sub, true);
        range.setGravity(Gravity.CENTER);
        range.setBackground(Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 9),
                Palette.alpha(p.primary, 20)));
        range.setContentDescription("打开日历");
        range.setOnClickListener(v -> CalendarSheet.open(a, selectedDate, date -> {
            a.agendaSelectedDate = Dates.date(date);
            a.agendaWeekMode = false;
            a.refresh();
        }));
        nav.addView(range, new LinearLayout.LayoutParams(0,
                Ui.dp(a, 36), 1));
        ImageView next = Ui.iconButton(a, R.drawable.ic_chevron_right, p.sub, p, false);
        next.setContentDescription("下一周");
        next.setOnClickListener(v -> {
            a.agendaSelectedDate = Dates.date(selectedDate.plusWeeks(1));
            a.refresh();
        });
        nav.addView(next);
        wrap.addView(nav);

        LinearLayout days = Ui.row(a);
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            boolean selected = date.equals(selectedDate);
            boolean today = date.equals(Dates.today());
            LinearLayout cell = Ui.column(a);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Ui.dp(a, 7), 0, Ui.dp(a, 7));
            cell.setBackground(selected ? Ui.bg(a, p.primary, 13)
                    : Ui.pressable(a, Color.TRANSPARENT, Ui.dp(a, 13), Palette.alpha(p.ink, 14)));
            TextView wd = Ui.text(a, Dates.weekShort(i + 1), 10,
                    selected ? Palette.alpha(p.onPrimary, 210) : p.faint, true);
            wd.setGravity(Gravity.CENTER);
            wd.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(wd);
            TextView num = Ui.text(a, String.valueOf(date.getDayOfMonth()), 16,
                    selected ? p.onPrimary : (today ? p.primary : p.ink), selected);
            num.setGravity(Gravity.CENTER);
            num.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            num.setPadding(0, Ui.dp(a, 3), 0, 0);
            cell.addView(num);
            if (today) {
                View dot = new View(a);
                dot.setBackground(Ui.bg(a, selected ? p.onPrimary : p.primary, 2));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(a, 3), Ui.dp(a, 3));
                dlp.topMargin = Ui.dp(a, 3);
                cell.addView(dot, dlp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, i == 6 ? 0 : 3);
            days.addView(cell, lp);
            cell.setClickable(true);
            cell.setOnClickListener(v -> {
                a.agendaSelectedDate = Dates.date(date);
                a.refresh();
            });
        }
        wrap.addView(days);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 2);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private LinearLayout sectionHeader(String label, String count) {
        LinearLayout row = Ui.row(a);
        row.addView(Ui.section(a, label, p), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.caption(a, count, p));
        return row;
    }

    private View emptyRow(String title, String subtitle) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 2), Ui.dp(a, 14), Ui.dp(a, 2), Ui.dp(a, 14));
        View dot = new View(a);
        dot.setBackground(Ui.bg(a, p.line, 3));
        row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(a, 6), Ui.dp(a, 6)));
        LinearLayout lines = Ui.column(a);
        lines.setPadding(Ui.dp(a, 10), 0, 0, 0);
        lines.addView(Ui.text(a, title, 13, p.sub, true));
        lines.addView(Ui.text(a, subtitle, 11.5f, p.faint, false));
        row.addView(lines);
        return row;
    }

    private LinearLayout courseCard(Occ occ, boolean first) {
        Store.TimeSlot slot = occ.startSlot;
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.stroke(a, p.surface, p.line, 1, 17));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
        card.setElevation(Ui.dp(a, 0.4f));

        LinearLayout time = Ui.column(a);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        time.setMinimumWidth(Ui.dp(a, 51));
        String start = slot.segments.isEmpty() ? "" : slot.segments.get(0).start;
        String end = occ.endSlot.segments.isEmpty() ? ""
                : occ.endSlot.segments.get(occ.endSlot.segments.size() - 1).end;
        TextView s = Ui.text(a, start, 13.5f, p.ink, true);
        s.setGravity(Gravity.CENTER);
        time.addView(s);
        TextView e = Ui.text(a, end, 9.5f, p.faint, false);
        e.setGravity(Gravity.CENTER);
        e.setPadding(0, Ui.dp(a, 2), 0, 0);
        time.addView(e);
        card.addView(time);

        View strip = new View(a);
        strip.setBackground(Ui.bg(a, occ.course.color, 3));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(a, 3), Ui.dp(a, 54));
        slp.setMargins(Ui.dp(a, 10), 0, Ui.dp(a, 11), 0);
        card.addView(strip, slp);

        LinearLayout info = Ui.column(a);
        LinearLayout titleRow = Ui.row(a);
        titleRow.addView(Ui.text(a, occ.course.name, 15, p.ink, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(Ui.pill(a, Store.sessionSlotLabel(table, occ.session), occ.course.color, p));
        info.addView(titleRow);
        LinearLayout meta = Ui.row(a);
        meta.setPadding(0, Ui.dp(a, 6), 0, 0);
        String location = Store.effectiveLocation(occ.course, occ.session);
        String teacherLine = Store.teacherLine(occ.course, occ.session);
        if (!location.isEmpty()) {
            meta.addView(Ui.icon(a, R.drawable.ic_location, 13, p.faint));
            TextView loc = Ui.text(a, location, 11.5f, p.sub, false);
            loc.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 10), 0);
            meta.addView(loc);
        }
        if (!teacherLine.isEmpty()) {
            meta.addView(Ui.icon(a, R.drawable.ic_person, 13, p.faint));
            TextView tt = Ui.text(a, teacherLine, 11.5f, p.sub, false);
            tt.setPadding(Ui.dp(a, 4), 0, 0, 0);
            meta.addView(tt);
        }
        info.addView(meta);
        if (slot.segments.size() > 1) {
            TextView segs = Ui.text(a, slot.segmentLabel(), 10, p.faint, false);
            segs.setPadding(0, Ui.dp(a, 5), 0, 0);
            info.addView(segs);
        }
        card.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.setClickable(true);
        card.setOnClickListener(v -> TimetableSheets.courseDetail(a, occ.course, selectedDate));
        return card;
    }

    private LinearLayout todoRow(Store.Todo todo, LocalDate date) {
        LinearLayout row = Ui.row(a);
        row.setBackground(Ui.stroke(a, p.surface, p.line, 1, 15));
        row.setPadding(Ui.dp(a, 11), Ui.dp(a, 9), Ui.dp(a, 11), Ui.dp(a, 9));
        ImageView check = Ui.icon(a, todo.done ? R.drawable.ic_check_circle : R.drawable.ic_circle,
                21, todo.done ? p.faint : (isOverdue(todo) ? p.danger : p.primary));
        check.setPadding(Ui.dp(a, 0), Ui.dp(a, 0), Ui.dp(a, 0), Ui.dp(a, 0));
        LinearLayout checkbox = Ui.row(a);
        checkbox.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
        checkbox.addView(check);
        checkbox.setClickable(true);
        checkbox.setOnClickListener(v -> toggle(todo));
        row.addView(checkbox);

        LinearLayout info = Ui.column(a);
        TextView title = Ui.text(a, todo.title, 13.5f,
                todo.done ? p.faint : p.ink, todo.done ? false : true);
        if (todo.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        info.addView(title);
        LinearLayout meta = Ui.row(a);
        meta.setPadding(0, Ui.dp(a, 3), 0, 0);
        if (todo.remindAt > 0 || todo.dueAt > 0) {
            StringBuilder times = new StringBuilder();
            if (todo.remindAt > 0) times.append("开始 ").append(Dates.time(Dates.timeOf(todo.remindAt)));
            if (todo.dueAt > 0) {
                if (times.length() > 0) times.append("  ·  ");
                times.append("截止 ").append(Dates.time(Dates.timeOf(todo.dueAt)));
            }
            TextView due = Ui.text(a, times.toString(),
                    10.5f, isOverdue(todo) ? p.danger : p.faint, false);
            meta.addView(due);
            if (todo.note != null && !todo.note.trim().isEmpty()) {
                TextView dot = Ui.text(a, "  ·  " + WeekSpec.stripForPreview(todo.note), 10.5f, p.faint, false);
                dot.setMaxLines(1);
                dot.setEllipsize(android.text.TextUtils.TruncateAt.END);
                meta.addView(dot, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
        }
        info.addView(meta);
        row.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (todo.priority > 0 && !todo.done) {
            row.addView(Ui.pill(a, todo.priority >= 2 ? "重要" : "优先", p.accent, p));
        }
        return row;
    }

    private boolean isOverdue(Store.Todo t) {
        return !t.done && t.dueAt > 0 && t.dueAt < System.currentTimeMillis();
    }

    private void toggle(Store.Todo todo) {
        todo.done = !todo.done;
        todo.doneAt = todo.done ? System.currentTimeMillis() : 0;
        store.save();
        if (todo.done) AlarmScheduler.cancel(a, todo);
        else AlarmScheduler.schedule(a, todo);
        a.refresh();
    }

    // --------------------------------------------------------------- week

    private void buildWeek(LinearLayout body) {
        LinearLayout nav = Ui.row(a);
        nav.setBackground(Ui.stroke(a, p.surface, p.line, 1, 15));
        nav.setPadding(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
        ImageView prev = Ui.iconButton(a, R.drawable.ic_chevron_left, p.sub, p, false);
        prev.setContentDescription("上一周");
        prev.setOnClickListener(v -> {
            a.agendaWeekOffset--;
            a.refresh();
        });
        nav.addView(prev);
        TextView center = Ui.text(a, Dates.weekRange(weekMonday), 12, p.ink, true);
        center.setGravity(Gravity.CENTER);
        nav.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView next = Ui.iconButton(a, R.drawable.ic_chevron_right, p.sub, p, false);
        next.setContentDescription("下一周");
        next.setOnClickListener(v -> {
            a.agendaWeekOffset++;
            a.refresh();
        });
        nav.addView(next);
        body.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int totalCourses = 0;
        int totalTodos = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekMonday.plusDays(i);
            totalCourses += occurrences(date).size();
            totalTodos += store.todosOn(date).size();
        }
        LinearLayout stats = Ui.row(a);
        stats.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
        stats.addView(Ui.caption(a, "本周 " + totalCourses + " 节课  ·  " + totalTodos + " 项待办", p),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (a.agendaWeekOffset != 0) {
            TextView back = Ui.textButton(a, "回到本周", p.primary, p);
            back.setOnClickListener(v -> {
                a.agendaWeekOffset = 0;
                a.refresh();
            });
            stats.addView(back);
        }
        body.addView(stats);

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekMonday.plusDays(i);
            body.addView(daySection(date), sectionCardLp());
        }
    }

    private LinearLayout.LayoutParams sectionCardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 10);
        return lp;
    }

    private View daySection(LocalDate date) {
        boolean today = date.equals(Dates.today());
        LinearLayout card = Ui.column(a);
        card.setBackground(Ui.stroke(a, today ? p.primarySoft : p.surface, p.line, 1, 18));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 12));

        LinearLayout head = Ui.row(a);
        TextView dateNum = Ui.text(a, date.getDayOfMonth() + "日", 19,
                today ? p.primary : p.ink, true);
        dateNum.setMinWidth(Ui.dp(a, 44));
        head.addView(dateNum);
        LinearLayout labels = Ui.column(a);
        labels.addView(Ui.text(a, Dates.weekFull(Dates.dow(date)), 11.5f, p.sub, true));
        labels.addView(Ui.caption(a, Dates.monthDay(date), p));
        head.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (today) head.addView(Ui.pill(a, "今天", p.primary, p));
        card.addView(head);

        List<Occ> occurrences = occurrences(date);
        List<Store.Todo> todos = store.todosOn(date);
        if (occurrences.isEmpty() && todos.isEmpty()) {
            TextView none = Ui.text(a, "没有安排", 11.5f, p.faint, false);
            none.setPadding(0, Ui.dp(a, 9), 0, 0);
            card.addView(none);
            return card;
        }
        for (Occ occ : occurrences) {
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 8), 0, 0);
            TextView t = Ui.text(a, occ.startSlot.segments.isEmpty() ? ""
                            : occ.startSlot.segments.get(0).start,
                    11, p.faint, false);
            t.setMinWidth(Ui.dp(a, 40));
            row.addView(t);
            View dot = new View(a);
            dot.setBackground(Ui.bg(a, occ.course.color, 3));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(a, 6), Ui.dp(a, 6));
            dlp.rightMargin = Ui.dp(a, 8);
            row.addView(dot, dlp);
            LinearLayout lines = Ui.column(a);
            lines.addView(Ui.text(a, occ.course.name, 12.5f, p.ink, true));
            String loc = Store.effectiveLocation(occ.course, occ.session);
            if (!loc.isEmpty()) lines.addView(Ui.text(a, loc, 10, p.faint, false));
            row.addView(lines, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setClickable(true);
            row.setOnClickListener(v -> TimetableSheets.courseDetail(a, occ.course, date));
            card.addView(row);
        }
        for (Store.Todo todo : todos) {
            LinearLayout row = Ui.row(a);
            row.setPadding(0, Ui.dp(a, 7), 0, 0);
            ImageView check = Ui.icon(a, todo.done ? R.drawable.ic_check_circle : R.drawable.ic_circle,
                    17, todo.done ? p.faint : p.primary);
            LinearLayout checkbox = Ui.row(a);
            checkbox.addView(check);
            checkbox.setPadding(0, Ui.dp(a, 2), Ui.dp(a, 9), Ui.dp(a, 2));
            checkbox.setClickable(true);
            checkbox.setOnClickListener(v -> toggle(todo));
            row.addView(checkbox);
            TextView title = Ui.text(a, todo.title, 12,
                    todo.done ? p.faint : p.sub, false);
            if (todo.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (todo.dueAt > 0) row.addView(Ui.caption(a, Dates.time(Dates.timeOf(todo.dueAt)), p));
            card.addView(row);
        }
        return card;
    }

    private List<Occ> occurrences(LocalDate date) {
        List<Occ> list = new ArrayList<>();
        if (termStart == null) return list;
        for (Store.Course c : table.courses) {
            for (Store.Session s : c.sessions) {
                if (!Store.occursOn(c, s, date, termStart)) continue;
                Store.TimeSlot slot = Store.slotById(table, s.slotId);
                if (slot == null) continue;
                int endIdx = Store.sessionEndIndex(table, s);
                Occ occ = new Occ();
                occ.course = c;
                occ.session = s;
                occ.startSlot = slot;
                occ.endSlot = endIdx >= 0 ? table.timeSlots.get(endIdx) : slot;
                list.add(occ);
            }
        }
        list.sort(Comparator.comparingInt(o -> o.startSlot.startMinutes()));
        return list;
    }

    @Override
    public boolean onBack() {
        if (weekMode) {
            a.agendaWeekMode = false;
            a.refresh();
            return true;
        }
        return false;
    }
}
